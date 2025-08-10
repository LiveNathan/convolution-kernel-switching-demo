package dev.nathanlively.convolution_kernel_switching_demo;

public class MaskingFactorCalculator {
    private final SpectralFlatnessCalculator flatnessCalc = new SpectralFlatnessCalculator();
    private final SpectralCrestCalculator crestCalc = new SpectralCrestCalculator();

    double calculateMaskingFactor(double[] spectrum) {
        SpectralFlatnessCalculator spectralFlatnessCalculator = new SpectralFlatnessCalculator();
        SpectralCrestCalculator crestCalc = new SpectralCrestCalculator();
        double spectralFlatness = spectralFlatnessCalculator.calculateFlatness(spectrum);
        double spectralCrest = crestCalc.calculateCrest(spectrum);

        // Pure tones: very low flatness AND moderate crest (concentrated energy)
        if (spectralFlatness < 0.01 && spectralCrest > 300 && spectralCrest < 5000) {
            return 1.0;
        }

        // White noise: high flatness, low crest
        if (spectralFlatness > 0.3) {
            return 3.0;
        }

        // Transient/percussive content: very high spectral crest (>5000)
        // indicates spiky spectrum from transients
        if (spectralCrest > 5000) {
            // Jungle, EDM, and other transient-rich music
            if (spectralCrest > 10000) {
                return 2.8 + Math.min(0.2, (spectralCrest - 10000) / 20000);
            }
            // Map crest 5000-10000 to factor 2.0-2.8
            return 2.0 + 0.8 * (spectralCrest - 5000) / 5000;
        }

        // Default case: use spectral flatness with adjusted mapping
        // This handles speech and other moderate content
        double logFlatness = Math.log10(Math.max(1e-4, spectralFlatness));
        double normalizedLog = Math.min(1.0, Math.max(0.0, (logFlatness + 3.5) / 3.0));
        return 1.5 + normalizedLog;
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