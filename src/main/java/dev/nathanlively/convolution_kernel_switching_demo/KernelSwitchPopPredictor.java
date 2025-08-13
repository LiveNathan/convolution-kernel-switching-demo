package dev.nathanlively.convolution_kernel_switching_demo;

import org.apache.arrow.memory.util.CommonUtil;
import org.apache.commons.numbers.complex.Complex;

public class KernelSwitchPopPredictor {
    private final MaskingFactorCalculator maskingCalc = new MaskingFactorCalculator();
    private static final double[] BARK_CENTER_FREQUENCIES = {
            50, 150, 250, 350, 450, 570, 700, 840, 1000, 1170,
            1370, 1600, 1850, 2150, 2500, 2900, 3400, 4000, 4800, 5800,
            7000, 8500, 10500, 13500
    };

    private static final double[] BARK_DISCONTINUITY_THRESHOLDS = {
            0.012, 0.015, 0.018, 0.025, 0.035, 0.045, 0.055, 0.065,
            0.070, 0.075, 0.080, 0.085, 0.090, 0.095, 0.100, 0.110,
            0.120, 0.130, 0.140, 0.150, 0.160, 0.170, 0.180, 0.200
    };
    private final int sampleRate;

    public KernelSwitchPopPredictor(int sampleRate) {
        this.sampleRate = sampleRate;
    }

    public PerceptualImpact predictAudibility(double[] signal, double[] currentKernel,
                                              double[] candidateKernel, int switchIndex) {
        // 1. Calculate the actual discontinuity at switch point
        double currentOutput = convolve(signal, currentKernel, switchIndex);
        double candidateOutput = convolve(signal, candidateKernel, switchIndex);
        double rawDiscontinuity = Math.abs(candidateOutput - currentOutput);

        // 2. Extract larger context for spectral flux calculation
        int fluxContextSize = 2048;  // Enough for ~12 flux frames
        double[] fluxContext = extractSegment(signal, switchIndex, fluxContextSize);

        // 3. Calculate spectral flux on the larger context
        SpectralFluxCalculator fluxCalc = new SpectralFluxCalculator();
        double spectralFlux = fluxCalc.normalizedAverageFlux(fluxContext);

        // 4. For frequency analysis, use smaller window centered on switch
        int analysisWindowSize = 512;
        double[] analysisSegment = extractSegment(signal, switchIndex, analysisWindowSize);

        // Apply windowing for FFT
        double[] hannWindow = SignalTransformer.createHannWindow(analysisWindowSize);
        double[] windowedSegment = new double[analysisWindowSize];
        for (int i = 0; i < analysisWindowSize; i++) {
            windowedSegment[i] = analysisSegment[i] * hannWindow[i];
        }

        // 5. Get magnitude spectrum and power spectrum from windowed segment
        double[] magnitude = fft(windowedSegment);
        double[] powerSpectrum = SignalTransformer.powerSpectrum(windowedSegment);

        // 6. Find dominant frequency to select threshold
        int dominantBin = findPeakBin(magnitude);
        double dominantFreq = binToFrequency(dominantBin, sampleRate, analysisWindowSize);

        // 7. Get frequency-specific threshold
        double threshold = getThresholdForFrequency(dominantFreq);

        // 8. Apply masking adjustment using both power spectrum and flux
        double maskingFactor = calculateMaskingFactorWithFlux(powerSpectrum, spectralFlux);
        double effectiveThreshold = threshold * maskingFactor;

        // 9. Return perceptual impact
        return new PerceptualImpact(rawDiscontinuity / effectiveThreshold);
    }

    // Rename for clarity - this doesn't window, just extracts
    private double[] extractSegment(double[] signal, int centerIndex, int segmentSize) {
        double[] segment = new double[segmentSize];
        int halfSegment = segmentSize / 2;

        for (int i = 0; i < segmentSize; i++) {
            int signalIndex = centerIndex - halfSegment + i;
            if (signalIndex >= 0 && signalIndex < signal.length) {
                segment[i] = signal[signalIndex];
            }
        }
        return segment;
    }

    double calculateMaskingFactorWithFlux(double[] spectrum, double normalizedAverageSpectralFlux) {
        // We really only need spectral flatness for noise because 512 samples is not long enough to give high spectral flux.
        SpectralFlatnessCalculator flatnessCalc = new SpectralFlatnessCalculator();
        double spectralFlatness = flatnessCalc.calculateFlatness(spectrum);
        if (spectralFlatness > 0.3) {
            return 3.0; // White noise
        }
        return 1.0 + (2.0 * normalizedAverageSpectralFlux);
    }

    double normalizedFlux(double spectralFlux) {
        return Math.min(1.0, spectralFlux / 3000000);
    }

    // Add these missing methods:
    private double convolve(double[] signal, double[] kernel, int centerIndex) {
        if (kernel.length == 1) {
            // Simple gain kernel
            return centerIndex >= 0 && centerIndex < signal.length
                    ? signal[centerIndex] * kernel[0]
                    : 0.0;
        }

        // Full convolution for longer kernels
        double result = 0.0;
        int halfKernel = kernel.length / 2;

        for (int i = 0; i < kernel.length; i++) {
            int signalIndex = centerIndex - halfKernel + i;
            if (signalIndex >= 0 && signalIndex < signal.length) {
                result += signal[signalIndex] * kernel[i];
            }
        }
        return result;
    }

    private double[] fft(double[] signal) {
        // Assume signal is already windowed if needed
        double[] paddedSignal = SignalTransformer.pad(signal, CommonUtil.nextPowerOfTwo(signal.length));
        Complex[] transform = SignalTransformer.fft(paddedSignal);

        double[] magnitudes = new double[transform.length / 2];
        for (int i = 0; i < magnitudes.length; i++) {
            magnitudes[i] = transform[i].abs();
        }
        return magnitudes;
    }

    private int findPeakBin(double[] spectrum) {
        int peakBin = 0;
        double maxMagnitude = spectrum[0];

        for (int i = 1; i < spectrum.length; i++) {
            if (spectrum[i] > maxMagnitude) {
                maxMagnitude = spectrum[i];
                peakBin = i;
            }
        }
        return peakBin;
    }

    private double binToFrequency(int bin, int sampleRate, int fftSize) {
        return (double) bin * sampleRate / fftSize;
    }

    double getThresholdForFrequency(double frequency) {
        // Find the appropriate threshold using linear interpolation
        for (int i = 0; i < BARK_CENTER_FREQUENCIES.length - 1; i++) {
            if (frequency <= BARK_CENTER_FREQUENCIES[i + 1]) {
                double f1 = BARK_CENTER_FREQUENCIES[i];
                double f2 = BARK_CENTER_FREQUENCIES[i + 1];
                double t1 = BARK_DISCONTINUITY_THRESHOLDS[i];
                double t2 = BARK_DISCONTINUITY_THRESHOLDS[i + 1];

                // Linear interpolation
                double ratio = (frequency - f1) / (f2 - f1);
                return t1 + ratio * (t2 - t1);
            }
        }
        // Above highest frequency
        return BARK_DISCONTINUITY_THRESHOLDS[BARK_DISCONTINUITY_THRESHOLDS.length - 1];
    }
}
