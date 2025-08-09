package dev.nathanlively.convolution_kernel_switching_demo;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class SignalTransformerTest {
    @Test
    void powerSpectrumSize() {
        // Power spectrum should return N/2 + 1 points for real signals
        double[] signal = new double[1024];
        Arrays.fill(signal, 1.0);

        double[] powerSpectrum = SignalTransformer.powerSpectrum(signal);

        // FFT size is padded to next power of 2 (1024 already is)
        // One-sided spectrum has N/2 + 1 points
        assertThat(powerSpectrum).hasSize(513);
    }

    @Test
    void powerSpectrumParsevalsTheorem() {
        // Parseval's theorem: energy in time domain equals energy in frequency domain
        double[] signal = new AudioSignalBuilder()
                .withLength(512)
                .withWhiteNoise(1.0)
                .withRandom(new Random(42))
                .build();

        // Energy in time domain
        double timeEnergy = Arrays.stream(signal)
                .map(x -> x * x)
                .sum();

        double[] powerSpectrum = SignalTransformer.powerSpectrum(signal);

        // Energy in frequency domain (accounting for one-sided spectrum)
        // DC and Nyquist bins count once, all others count twice
        double freqEnergy = powerSpectrum[0];  // DC
        for (int i = 1; i < powerSpectrum.length - 1; i++) {
            freqEnergy += 2 * powerSpectrum[i];  // Double for one-sided spectrum
        }
        freqEnergy += powerSpectrum[powerSpectrum.length - 1];  // Nyquist

        // Normalize by FFT size
        int fftSize = 512;  // Next power of 2 for 512 is 512
        freqEnergy /= fftSize;

        // Should be approximately equal (within numerical precision)
        assertThat(freqEnergy).isCloseTo(timeEnergy, within(1e-10));
    }



    @Test
    void powerSpectrumOfPureSine() {
        // Pure sine should have a peak at its frequency
        int sampleRate = 1000;
        int signalLength = 1000;
        int frequency = 100;  // 100 Hz
        double[] signal = new AudioSignalBuilder()
                .withLength(signalLength)
                .withSampleRate(sampleRate)
                .withSineWave(frequency, 1.0)
                .build();
        double[] powerSpectrum = SignalTransformer.powerSpectrum(signal);
        // Find the peak
        int maxIndex = 0;
        double maxValue = powerSpectrum[0];
        for (int i = 1; i < powerSpectrum.length; i++) {
            if (powerSpectrum[i] > maxValue) {
                maxValue = powerSpectrum[i];
                maxIndex = i;
            }
        }
        // Convert bin index to frequency
        int fftSize = 1024;  // Next power of 2 for 1000
        double binFrequency = (double) maxIndex * sampleRate / fftSize;
        // Peak should be at approximately 100 Hz
        assertThat(binFrequency).isCloseTo(frequency, within(5.0));
        // All values should be non-negative
        assertThat(Arrays.stream(powerSpectrum).allMatch(value -> value >= 0.0)).isTrue();
    }

}