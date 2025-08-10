package dev.nathanlively.convolution_kernel_switching_demo;

import java.util.Arrays;

public class SpectralCrestCalculator {
    public double calculateCrest(double[] spectrum) {
        if (spectrum.length == 0) return 0.0;

        double maximum = Arrays.stream(spectrum).max().orElse(0.0);
        double mean = Arrays.stream(spectrum).average().orElse(0.0);

        return mean > 0 ? maximum / mean : 0.0;
    }
}
