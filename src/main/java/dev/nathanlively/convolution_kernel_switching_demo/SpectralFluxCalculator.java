package dev.nathanlively.convolution_kernel_switching_demo;

import org.apache.commons.numbers.complex.Complex;

public class SpectralFluxCalculator {
    private static final int WINDOW_SIZE = 512;
    private static final int HOP_SIZE = WINDOW_SIZE / 4; // 75% overlap
    private static final double NORMALIZATION_FACTOR = 14.253; // Based on actual observed max values

    public double normalizedAverageFlux(double[] signal) {
        if (signal.length < WINDOW_SIZE * 2) {
            // Not enough data for meaningful flux calculation
            return 0.0;
        }

        double[] window = SignalTransformer.createHannWindow(WINDOW_SIZE);
        double totalFlux = 0.0;
        int fluxCount = 0;

        double[] previousMagnitudes = null;

        for (int pos = 0; pos <= signal.length - WINDOW_SIZE; pos += HOP_SIZE) {
            double[] windowedFrame = new double[WINDOW_SIZE];
            for (int i = 0; i < WINDOW_SIZE; i++) {
                windowedFrame[i] = signal[pos + i] * window[i];
            }

            Complex[] spectrum = SignalTransformer.fft(SignalTransformer.pad(windowedFrame,
                    SignalTransformer.calculateOptimalFftSize(WINDOW_SIZE, 1)));
            double[] magnitudes = new double[spectrum.length / 2];
            for (int i = 0; i < magnitudes.length; i++) {
                magnitudes[i] = spectrum[i].abs();
            }

            if (previousMagnitudes != null) {
                double frameFlux = 0.0;
                for (int i = 0; i < magnitudes.length; i++) {
                    double diff = magnitudes[i] - previousMagnitudes[i];
                    if (diff > 0) {
                        frameFlux += diff * diff;
                    }
                }
                totalFlux += Math.sqrt(frameFlux);
                fluxCount++;
            }

            previousMagnitudes = magnitudes;
        }

        double rawFlux = fluxCount > 0 ? totalFlux / fluxCount : 0.0;
        return Math.min(1.0, rawFlux / NORMALIZATION_FACTOR);
    }

}