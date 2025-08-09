package dev.nathanlively.convolution_kernel_switching_demo;

import java.util.Arrays;

public class SpectralFlatnessCalculator {
    public double calculateFlatness(double[] powerSpectrum) {
        // Filter out zeros to avoid log(0)
        double[] nonZero = Arrays.stream(powerSpectrum)
                .filter(x -> x > 1e-10)
                .toArray();

        if (nonZero.length == 0) return 0.0;

        double logSum = Arrays.stream(nonZero)
                .map(Math::log)
                .sum();
        double geometricMean = Math.exp(logSum / nonZero.length);
        double arithmeticMean = Arrays.stream(nonZero).average().orElse(0.0);

        return geometricMean / arithmeticMean;
    }
}
