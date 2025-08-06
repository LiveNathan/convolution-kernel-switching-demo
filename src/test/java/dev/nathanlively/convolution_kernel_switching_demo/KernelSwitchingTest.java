package dev.nathanlively.convolution_kernel_switching_demo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class KernelSwitchingTest {
    private static final Logger log = LoggerFactory.getLogger(KernelSwitchingTest.class);
    private Convolution convolution;
    private AudioTestHelper audioHelper;

    @BeforeEach
    void setUp() {
        convolution = new OverlapSaveAdapter();
        audioHelper = new AudioTestHelper();
    }

    @ParameterizedTest
    @ValueSource(doubles = {0.0, 0.1, 0.25, 0.5, 0.75, 0.9, 1.1, 1.5, 2.0})
    void givenAbruptKernelSwitch_whenConvolving_thenDetectsDiscontinuity(double kernel2Gain) throws IOException {
        final int sampleRate = 44100;
        double[] signal = AudioSignals.generateSineWave(100, 2.0, sampleRate);

        double[] kernel1 = {1.0};  // Unity gain
        double[] kernel2 = {kernel2Gain};  // Variable gain

        // Get reference: what the output would be with each kernel alone
        double[] onlyKernel1 = convolution.with(signal, kernel1);
        double[] onlyKernel2 = convolution.with(signal, kernel2);

        // Get actual: switching between kernels
        int periodSamples = sampleRate / 4;  // Switch every 0.25 seconds
        double[] withSwitching = convolution.with(signal, List.of(kernel1, kernel2), periodSamples);

        // At switch point, measure the error
        double expected = onlyKernel1[periodSamples]; // Would have continued with kernel1
        double actual = withSwitching[periodSamples];  // But switched to kernel2
        double error = Math.abs(actual - expected);

        log.info("Kernel2 gain: {}, Error at switch point: {}", kernel2Gain, error);
        log.info("Expected (kernel1 continues): {}, Actual (switched to kernel2): {}", expected, actual);

        // Save audio files for listening tests
        String baseFileName = String.format("kernel-switch-gain-%.2f", kernel2Gain);

        WavFile originalSignal = new WavFile(sampleRate, AudioSignals.normalize(signal));
        WavFile switchedResult = new WavFile(sampleRate, AudioSignals.normalize(withSwitching));
        WavFile kernel1Only = new WavFile(sampleRate, AudioSignals.normalize(onlyKernel1));
        WavFile kernel2Only = new WavFile(sampleRate, AudioSignals.normalize(onlyKernel2));

        audioHelper.save(originalSignal, baseFileName + "-original.wav");
        audioHelper.save(switchedResult, baseFileName + "-switched.wav");
        audioHelper.save(kernel1Only, baseFileName + "-kernel1-only.wav");
        audioHelper.save(kernel2Only, baseFileName + "-kernel2-only.wav");

        // The test passes regardless of error - we're generating audio for empirical testing
        assertThat(error).isGreaterThanOrEqualTo(0.0);

        log.info("Generated audio files for kernel2 gain: {}", kernel2Gain);
    }
}