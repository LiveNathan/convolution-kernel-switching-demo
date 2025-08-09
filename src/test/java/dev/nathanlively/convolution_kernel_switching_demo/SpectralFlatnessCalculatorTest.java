package dev.nathanlively.convolution_kernel_switching_demo;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

class SpectralFlatnessCalculatorTest {
    @Test
    void spectralFlatness() {
        SpectralFlatnessCalculator calculator = new SpectralFlatnessCalculator();

        // Use a fixed seed for reproducible tests
        double[] noise = new AudioSignalBuilder()
                .withLength(1024)
                .withWhiteNoise(0.5)
                .withRandom(new Random(42))
                .build();

        double[] powerSpectrum = SignalTransformer.powerSpectrum(noise);

        double actual = calculator.calculateFlatness(powerSpectrum);

        // Based on manual FFT MATLAB analysis: white noise typically 0.52-0.61
        assertThat(actual).isBetween(0.52, 0.615);
    }

    @Test
    void spectralFlatnessForPureTone() {
        SpectralFlatnessCalculator calculator = new SpectralFlatnessCalculator();

        double[] sine = new AudioSignalBuilder()
                .withLength(1024)
                .withSampleRate(44100)
                .withSineWave(440, 1.0)
                .build();

        double[] powerSpectrum = SignalTransformer.powerSpectrum(sine);

        double actual = calculator.calculateFlatness(powerSpectrum);

        // Based on manual FFT: pure tone should be around 0.002
        assertThat(actual).isLessThan(0.003);
    }

}