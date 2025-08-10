package dev.nathanlively.convolution_kernel_switching_demo;

public class MaskingFactorCalculator {
    private final SpectralFlatnessCalculator flatnessCalc = new SpectralFlatnessCalculator();
    private final SpectralCrestCalculator crestCalc = new SpectralCrestCalculator();

    public double calculateMaskingFactor(double[] powerSpectrum) {
        double flatness = flatnessCalc.calculateFlatness(powerSpectrum);
        double crest = crestCalc.calculateCrest(powerSpectrum);

        // Normalize flatness using logarithmic mapping (from your original implementation)
        double logFlatness = Math.log10(Math.max(1e-4, flatness));
        double normalizedFlatness = Math.min(1.0, Math.max(0.0, (logFlatness + 4.0) / 3.8));

        // Normalize crest: higher crest = more tonal = less masking
        // Using empirical scaling factor based on MATLAB analysis (range 8.7 to 13,541)
        double normalizedCrest = 1.0 / (1.0 + crest * 0.0001);

        // Combine both measures
        // Higher values = more noise-like = more masking
        double combinedMeasure = 0.8 * normalizedFlatness + 0.2 * normalizedCrest;

        // Map to masking factor range [1.0, 3.0]
        // 1.0 = minimal masking (pure tones)
        // 3.0 = maximum masking (white noise)
        return 1.0 + (2.0 * combinedMeasure);
    }

    /**
     * Alternative approach using perceptual categories
     */
    public double calculateMaskingFactorPerceptual(double[] powerSpectrum) {
        double flatness = flatnessCalc.calculateFlatness(powerSpectrum);
        double crest = crestCalc.calculateCrest(powerSpectrum);

        // Categorize based on empirical thresholds
        if (flatness > 0.1) {
            return 3.0; // High flatness = noise-like = maximum masking
        } else if (crest < 50) {
            return 2.5; // Low crest = distributed energy = high masking
        } else if (flatness < 0.001 && crest > 1000) {
            return 1.0; // Very tonal = minimal masking
        } else {
            return 2.0; // Moderate content = moderate masking
        }
    }
}