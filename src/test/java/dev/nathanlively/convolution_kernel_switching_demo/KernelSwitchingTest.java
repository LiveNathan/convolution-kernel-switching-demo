package dev.nathanlively.convolution_kernel_switching_demo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class KernelSwitchingTest {
    private static final Logger log = LoggerFactory.getLogger(KernelSwitchingTest.class);
    private Convolution convolution;

    @BeforeEach
    void setUp() {
        convolution = new OverlapSaveAdapter();
    }

    @Test
    void givenAbruptKernelSwitch_whenConvolving_thenDetectsDiscontinuity() {
        final int sampleRate = 44100;
        double[] signal = generateSineWave(100, 2.0, sampleRate);

        double[] kernel1 = {1.0};  // Unity gain
        double[] kernel2 = {0.5};  // Half gain

        // Get reference: what the output would be with each kernel alone
        double[] onlyKernel1 = convolution.with(signal, kernel1);
        double[] onlyKernel2 = convolution.with(signal, kernel2);

        // Get actual: switching between kernels
        int periodSamples = sampleRate / 4;
        double[] withSwitching = convolution.with(signal, List.of(kernel1, kernel2), periodSamples);

        // At switch point, measure the error
        double expected = onlyKernel1[periodSamples]; // Would have continued with kernel1
        double actual = withSwitching[periodSamples];  // But switched to kernel2
        double error = Math.abs(actual - expected);

        log.info("Error at switch point: {}", error);
        log.info("Expected (kernel1 continues): {}", expected);
        log.info("Actual (switched to kernel2): {}", actual);

        // This measures the actual discontinuity that creates the pop
        assertThat(error).isLessThan(0.01);
    }

    private double[] generateSineWave(int frequency, double seconds, int sampleRate) {
        double[] signal = new double[sampleRate * (int) seconds];
        for (int i = 0; i < signal.length; i++) {
            signal[i] = Math.sin(2 * Math.PI * frequency * i / sampleRate);
        }
        return signal;
    }
}