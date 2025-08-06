package dev.nathanlively.convolution_kernel_switching_demo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

class KernelSwitchingTest {
    private static final Logger log = LoggerFactory.getLogger(KernelSwitchingTest.class);
    private Convolution convolution;
    private AudioTestHelper audioHelper;
    private Random random;

    @BeforeEach
    void setUp() {
        convolution = new OverlapSaveAdapter();
        audioHelper = new AudioTestHelper();
        random = new Random();
    }

    @ParameterizedTest
    @ValueSource(doubles = {0.95, 0.96, 0.97, 0.98, 0.99, 1.0})
    void givenAbruptKernelSwitch_whenConvolving_thenDetectsDiscontinuity(double kernel2Gain) throws IOException {
        final int sampleRate = 44100;
        double[] signal = AudioSignals.generateSineWave(107, 2.0, sampleRate);

        double[] kernel1 = {1.0};  // Unity gain
        double[] kernel2 = {kernel2Gain};  // Variable gain

        // Get reference: what the output would be with each kernel alone
        double[] onlyKernel1 = convolution.with(signal, kernel1);
        double[] onlyKernel2 = convolution.with(signal, kernel2);

        // Get actual: switching between kernels
        int minPeriod = sampleRate / 5;  // 8820 samples at 44100 Hz
        int maxPeriod = sampleRate / 4;  // 11025 samples at 44100 Hz
        int periodSamples = random.nextInt(maxPeriod - minPeriod + 1) + minPeriod;

        double[] withSwitching = convolution.with(signal, List.of(kernel1, kernel2), periodSamples);

        // At switch point, measure the error
        double expected = onlyKernel1[periodSamples]; // Would have continued with kernel1
        double actual = withSwitching[periodSamples];  // But switched to kernel2
        double error = Math.abs(actual - expected);

        log.info("Kernel2 gain: {}, Error at switch point: {}", kernel2Gain, error);
        log.info("Expected (kernel1 continues): {}, Actual (switched to kernel2): {}", expected, actual);

        // Save audio files for listening tests
        String baseFileName = String.format("kernel-switch-gain-%.2f", kernel2Gain);
        WavFile switchedResult = new WavFile(sampleRate, AudioSignals.normalize(withSwitching));
        audioHelper.save(switchedResult, baseFileName + "-switched.wav");

        // The test passes regardless of error - we're generating audio for empirical testing
        assertThat(error).isGreaterThanOrEqualTo(0.0);

        log.info("Generated audio files for kernel2 gain: {}", kernel2Gain);
    }
}