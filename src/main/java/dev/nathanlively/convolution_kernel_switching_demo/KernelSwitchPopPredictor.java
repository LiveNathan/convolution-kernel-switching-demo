package dev.nathanlively.convolution_kernel_switching_demo;

// https://claude.ai/chat/e731fc8f-503f-4ed9-8d6e-bb69c2a7629e
public class KernelSwitchPopPredictor {
    public double predictAudibility(double[] signal, double[] currentKernel,
                                       double[] candidateKernel, int switchIndex) {
        // 1. Calculate the actual discontinuity at switch point
        double currentOutput = convolve(signal, currentKernel, switchIndex);
        double candidateOutput = convolve(signal, candidateKernel, switchIndex);
        double rawDiscontinuity = Math.abs(candidateOutput - currentOutput);

        // 2. Analyze frequency content around switch point
        int windowSize = 512;
        double[] analysisWindow = extractWindow(signal, switchIndex, windowSize);
        double[] spectrum = fft(analysisWindow);
        double[] powerSpectrum = SignalTransformer.powerSpectrum(analysisWindow);

        // 3. Find dominant frequency to select threshold
        int dominantBin = findPeakBin(spectrum);
        double dominantFreq = binToFrequency(dominantBin, sampleRate, windowSize);

        // 4. Get frequency-specific threshold
        double threshold = getThresholdForFrequency(dominantFreq);

        // 5. Apply masking adjustment based on signal complexity
        double maskingFactor = calculateMaskingFactor(powerSpectrum);
        double effectiveThreshold = threshold * maskingFactor;

        // 6. Return perceptual impact (0 = inaudible, >1 = clearly audible)
        return rawDiscontinuity / effectiveThreshold;
    }

    private double calculateMaskingFactor(double[] spectrum) {
        // Complex signals mask discontinuities better
        SpectralFlatnessCalculator spectralFlatnessCalculator = new SpectralFlatnessCalculator();
        double spectralFlatness = spectralFlatnessCalculator.calculateFlatness(spectrum);
        // Returns 1.0 for pure tones, up to 3.0 for noise
        return 1.0 + (2.0 * spectralFlatness);
    }
}
