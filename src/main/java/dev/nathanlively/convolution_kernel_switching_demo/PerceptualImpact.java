package dev.nathanlively.convolution_kernel_switching_demo;

public record PerceptualImpact(double ratio) {

    public boolean isAudible() {
        return ratio >= 1.0;
    }

    public boolean isInaudible() {
        return ratio < 1.0;
    }

    public AudibilityLevel level() {
        if (ratio < 0.5) return AudibilityLevel.WELL_BELOW_THRESHOLD;
        if (ratio < 1.0) return AudibilityLevel.BELOW_THRESHOLD;
        if (ratio < 2.0) return AudibilityLevel.SLIGHTLY_AUDIBLE;
        if (ratio < 5.0) return AudibilityLevel.CLEARLY_AUDIBLE;
        return AudibilityLevel.VERY_AUDIBLE;
    }

    public enum AudibilityLevel {
        WELL_BELOW_THRESHOLD,
        BELOW_THRESHOLD,
        SLIGHTLY_AUDIBLE,
        CLEARLY_AUDIBLE,
        VERY_AUDIBLE
    }
}