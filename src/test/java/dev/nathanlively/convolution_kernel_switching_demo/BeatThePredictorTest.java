package dev.nathanlively.convolution_kernel_switching_demo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BeatThePredictorTest {
    private static final Logger log = LoggerFactory.getLogger(BeatThePredictorTest.class);
    private static final int SAMPLE_RATE = 44100;

    private Convolution convolution;
    private AudioTestHelper audioHelper;
    private KernelSwitchPopPredictor predictor;

    @BeforeEach
    void setUp() {
        convolution = new OverlapSaveAdapter();
        audioHelper = new AudioTestHelper();
        predictor = new KernelSwitchPopPredictor(SAMPLE_RATE);
    }

    @Test
    void testCombFilterResonanceSwitch() throws IOException {
        // Create a broadband signal with rich harmonics
        double[] signal = createSawtoothWave(110, 3.0); // A2 note, rich harmonics

        // Identity kernel
        double[] kernel1 = {1.0};

        // Comb filter kernel - creates resonances/nulls in spectrum
        // This simulates a very short delay (2.27ms at 44.1kHz)
        double[] kernel2 = new double[101];
        kernel2[0] = 0.7;  // Direct signal
        kernel2[100] = 0.3; // Delayed signal - creates comb filtering

        // Pad kernel1 to match kernel2 length
        double[] paddedKernel1 = new double[101];
        paddedKernel1[0] = 1.0;

        // Switch in the middle of sustained note
        int switchIndex = SAMPLE_RATE + SAMPLE_RATE / 2;

        double[] result = convolution.with(signal,
                List.of(paddedKernel1, kernel2), switchIndex);

        // Measure discontinuity
        double maxDisc = findMaxDiscontinuityNearIndex(result, switchIndex, 200);

        // Predict audibility
        PerceptualImpact impact = predictor.predictAudibility(signal,
                paddedKernel1, kernel2, switchIndex);

        log.info("Comb filter switch - Discontinuity: {}, Impact: {}, Audible: {}",
                maxDisc, impact.ratio(), impact.isAudible());
        assertThat(impact.isInaudible()).isTrue();
        String filename = String.format("beat-comb-filter-disc-%.4f-impact-%.3f.wav",
                maxDisc, impact.ratio());
        audioHelper.save(new WavFile(SAMPLE_RATE,
                AudioSignals.normalize(result)), filename);

        // The comb filter creates audible coloration but might not show
        // as high discontinuity because it's a gradual spectral change
        log.info("Listen for metallic/hollow coloration change at 1.5 seconds");
    }

    @Test
    void testReverbTailCutoff() throws IOException {
        // Create a percussive signal
        double[] signal = createPercussiveSignal();

        // Simple reverb-like kernel (exponentially decaying impulse response)
        double[] kernel1 = createReverbKernel(0.3, 2000); // 300ms reverb
        double[] kernel2 = {1.0}; // Sudden dry signal

        // Pad kernel2 to match
        double[] paddedKernel2 = new double[kernel1.length];
        paddedKernel2[0] = 1.0;

        // Switch right after a transient
        int transientIndex = findNextTransient(signal, SAMPLE_RATE);
        int switchIndex = transientIndex + SAMPLE_RATE / 10; // 100ms after transient

        double[] result = convolution.with(signal,
                List.of(kernel1, paddedKernel2), switchIndex);

        PerceptualImpact impact = predictor.predictAudibility(signal,
                kernel1, paddedKernel2, switchIndex);

        assertThat(impact.isInaudible()).isTrue();
        double maxDisc = findMaxDiscontinuityNearIndex(result, switchIndex, 200);

        log.info("Reverb cutoff - Discontinuity: {}, Impact: {}, Audible: {}",
                maxDisc, impact.ratio(), impact.isAudible());

        String filename = String.format("beat-reverb-cutoff-disc-%.4f-impact-%.3f.wav",
                maxDisc, impact.ratio());
        audioHelper.save(new WavFile(SAMPLE_RATE,
                AudioSignals.normalize(result)), filename);

        // Sudden reverb cutoff is very audible but might not show high
        // discontinuity if it happens during decay
        log.info("Listen for sudden ambience disappearance");
    }

    @Test
    void testIntermodulationDistortion() throws IOException {
        // Create signal with two non-harmonically related frequencies
        // These will create audible intermodulation when processed
        double[] signal = createTwoToneSignal(300, 470); // Non-harmonic

        double[] kernel1 = {1.0};

        // Soft clipping kernel - creates intermodulation products
        double[] kernel2 = createSoftClippingKernel();

        int switchIndex = SAMPLE_RATE + SAMPLE_RATE / 2;

        double[] result = applyKernelSwitchManually(signal, kernel1, kernel2, switchIndex);

        PerceptualImpact impact = predictor.predictAudibility(signal,
                kernel1, kernel2, switchIndex);

        double maxDisc = findMaxDiscontinuityNearIndex(result, switchIndex, 200);

        log.info("Intermodulation switch - Discontinuity: {}, Impact: {}, Audible: {}",
                maxDisc, impact.ratio(), impact.isAudible());

        String filename = String.format("beat-intermod-disc-%.4f-impact-%.3f.wav",
                maxDisc, impact.ratio());
        audioHelper.save(new WavFile(SAMPLE_RATE,
                AudioSignals.normalize(result)), filename);

        log.info("Listen for new frequency components appearing (ghost tones)");
    }

    @Test
    void testSpectralHoleInSpeechFormant() throws IOException {
        // Load speech or create synthetic vowel sound
        double[] signal;
        try {
            WavFile speechFile = audioHelper.loadFromClasspath("Lecture5sec.wav");
            signal = speechFile.signal();
        } catch (Exception e) {
            // Fallback: create synthetic vowel-like sound
            signal = createSyntheticVowel(2.0);
        }

        double[] kernel1 = {1.0};

        // Notch filter at critical speech frequency (~2-3kHz)
        // This affects intelligibility significantly
        double[] kernel2 = createNotchFilter(2500, 200, SAMPLE_RATE);

        // Pad kernel1
        double[] paddedKernel1 = new double[kernel2.length];
        paddedKernel1[0] = 1.0;

        int switchIndex = SAMPLE_RATE / 2;

        double[] result = convolution.with(signal,
                List.of(paddedKernel1, kernel2), switchIndex);

        PerceptualImpact impact = predictor.predictAudibility(signal,
                paddedKernel1, kernel2, switchIndex);

        double maxDisc = findMaxDiscontinuityNearIndex(result, switchIndex, 200);

        log.info("Formant notch - Discontinuity: {}, Impact: {}, Audible: {}",
                maxDisc, impact.ratio(), impact.isAudible());

        String filename = String.format("beat-formant-notch-disc-%.4f-impact-%.3f.wav",
                maxDisc, impact.ratio());
        audioHelper.save(new WavFile(SAMPLE_RATE,
                AudioSignals.normalize(result)), filename);

        log.info("Listen for sudden change in speech clarity/timbre");
    }

    @Test
    void testGroupDelayDistortion() throws IOException {
        // Create a signal with sharp transients
        double[] signal = createChirpSignal(100, 4000, 3.0);

        double[] kernel1 = {1.0};

        // All-pass filter cascade - preserves magnitude but distorts phase
        double[] kernel2 = createAllpassCascade(0.7, 0.5, 0.3);

        // Pad kernel1
        double[] paddedKernel1 = new double[kernel2.length];
        paddedKernel1[0] = 1.0;

        int switchIndex = SAMPLE_RATE + SAMPLE_RATE / 2;

        double[] result = convolution.with(signal,
                List.of(paddedKernel1, kernel2), switchIndex);

        PerceptualImpact impact = predictor.predictAudibility(signal,
                paddedKernel1, kernel2, switchIndex);
assertThat(impact.isInaudible()).isTrue();
        double maxDisc = findMaxDiscontinuityNearIndex(result, switchIndex, 200);

        log.info("Group delay - Discontinuity: {}, Impact: {}, Audible: {}",
                maxDisc, impact.ratio(), impact.isAudible());

        String filename = String.format("beat-group-delay-disc-%.4f-impact-%.3f.wav",
                maxDisc, impact.ratio());
        audioHelper.save(new WavFile(SAMPLE_RATE,
                AudioSignals.normalize(result)), filename);

        log.info("Listen for smearing/pre-echo on the chirp sweep");
    }

    // Helper methods

    private double[] createSawtoothWave(double frequency, double duration) {
        int samples = (int) (SAMPLE_RATE * duration);
        double[] signal = new double[samples];
        double period = SAMPLE_RATE / frequency;

        for (int i = 0; i < samples; i++) {
            double phase = (i % period) / period;
            signal[i] = 2.0 * phase - 1.0;
        }
        return signal;
    }

    private double[] createPercussiveSignal() {
        int samples = SAMPLE_RATE * 3;
        double[] signal = new double[samples];

        // Add several percussive hits
        int[] hitIndices = {SAMPLE_RATE / 2, SAMPLE_RATE,
                (int) (SAMPLE_RATE * 1.5), SAMPLE_RATE * 2};

        for (int hit : hitIndices) {
            if (hit < samples) {
                // Exponentially decaying burst
                for (int i = 0; i < SAMPLE_RATE / 10 && hit + i < samples; i++) {
                    signal[hit + i] = Math.exp(-i * 0.0001) *
                                      Math.sin(2 * Math.PI * 200 * i / SAMPLE_RATE);
                }
            }
        }
        return signal;
    }

    private double[] createReverbKernel(double decayTime, int samples) {
        double[] kernel = new double[samples];
        double decay = Math.exp(-3.0 / (decayTime * SAMPLE_RATE));

        kernel[0] = 1.0; // Direct signal

        // Add early reflections
        int[] earlyDelays = {353, 457, 563, 701, 823};
        double[] earlyGains = {0.3, 0.25, 0.2, 0.15, 0.1};

        for (int i = 0; i < earlyDelays.length; i++) {
            if (earlyDelays[i] < samples) {
                kernel[earlyDelays[i]] = earlyGains[i];
            }
        }

        // Add diffuse tail
        for (int i = 1000; i < samples; i++) {
            kernel[i] = 0.1 * Math.pow(decay, i - 1000) *
                        (Math.random() - 0.5);
        }

        return kernel;
    }

    private int findNextTransient(double[] signal, int startIndex) {
        for (int i = startIndex + 1; i < signal.length - 1; i++) {
            double energy = Math.abs(signal[i]);
            double prevEnergy = Math.abs(signal[i - 1]);
            if (energy > prevEnergy * 3 && energy > 0.1) {
                return i;
            }
        }
        return startIndex + SAMPLE_RATE / 4; // Default
    }

    private double[] createTwoToneSignal(double freq1, double freq2) {
        int samples = SAMPLE_RATE * 3;
        double[] signal = new double[samples];

        for (int i = 0; i < samples; i++) {
            double t = (double) i / SAMPLE_RATE;
            signal[i] = 0.5 * Math.sin(2 * Math.PI * freq1 * t) +
                        0.5 * Math.sin(2 * Math.PI * freq2 * t);
        }
        return signal;
    }

    private double[] createSoftClippingKernel() {
        // This is a memoryless nonlinearity, so single-sample kernel
        // But we'll simulate it with a short kernel
        return new double[]{0.9}; // Slightly attenuated to avoid hard clipping
    }

    private double[] applyKernelSwitchManually(double[] signal, double[] kernel1,
                                               double[] kernel2, int switchIndex) {
        double[] result = signal.clone();

        // Apply soft clipping after the switch point
        for (int i = switchIndex; i < result.length; i++) {
            // Soft clipping: tanh-like function
            double x = result[i] * kernel2[0];
            result[i] = Math.tanh(x * 2) * 0.5; // Soft saturation
        }

        return result;
    }

    private double[] createSyntheticVowel(double duration) {
        int samples = (int) (SAMPLE_RATE * duration);
        double[] signal = new double[samples];

        // Formant frequencies for "ah" vowel
        double[] formants = {700, 1220, 2600};
        double[] amplitudes = {1.0, 0.5, 0.3};

        for (int i = 0; i < samples; i++) {
            double t = (double) i / SAMPLE_RATE;
            for (int f = 0; f < formants.length; f++) {
                signal[i] += amplitudes[f] *
                             Math.sin(2 * Math.PI * formants[f] * t);
            }
        }
        return signal;
    }

    private double[] createNotchFilter(double centerFreq, double bandwidth, int sampleRate) {
        // Simple FIR notch filter design
        int filterLength = 65;
        double[] kernel = new double[filterLength];
        int center = filterLength / 2;

        // Start with impulse
        kernel[center] = 1.0;

        // Subtract bandpass to create notch
        double normalizedFreq = centerFreq / sampleRate;
        double normalizedBandwidth = bandwidth / sampleRate;

        for (int i = 0; i < filterLength; i++) {
            if (i != center) {
                int n = i - center;
                double h = 2 * normalizedBandwidth *
                           Math.sin(2 * Math.PI * normalizedFreq * n) /
                           (Math.PI * n);
                kernel[i] = -h * (0.54 - 0.46 * Math.cos(2 * Math.PI * i / (filterLength - 1)));
            }
        }

        return kernel;
    }

    private double[] createChirpSignal(double startFreq, double endFreq, double duration) {
        int samples = (int) (SAMPLE_RATE * duration);
        double[] signal = new double[samples];

        double freqSlope = (endFreq - startFreq) / duration;

        for (int i = 0; i < samples; i++) {
            double t = (double) i / SAMPLE_RATE;
            double instantFreq = startFreq + freqSlope * t;
            double phase = 2 * Math.PI * (startFreq * t + 0.5 * freqSlope * t * t);
            signal[i] = Math.sin(phase);
        }
        return signal;
    }

    private double[] createAllpassCascade(double a1, double a2, double a3) {
        // Cascade of three first-order allpass filters
        int length = 64;
        double[] kernel = new double[length];

        // Initialize with first allpass
        kernel[0] = -a1;
        kernel[1] = 1;

        // Convolve with second allpass
        double[] temp = new double[length];
        temp[0] = kernel[0] * (-a2);
        temp[1] = kernel[0] + kernel[1] * (-a2);
        temp[2] = kernel[1];

        // Convolve with third allpass
        kernel[0] = temp[0] * (-a3);
        kernel[1] = temp[0] + temp[1] * (-a3);
        kernel[2] = temp[1] + temp[2] * (-a3);
        kernel[3] = temp[2];

        // Add some decay for stability
        for (int i = 4; i < length; i++) {
            kernel[i] = kernel[i - 1] * 0.9;
        }

        return kernel;
    }

    private double findMaxDiscontinuityNearIndex(double[] signal, int centerIndex, int radius) {
        double maxDiscontinuity = 0.0;
        int start = Math.max(1, centerIndex - radius);
        int end = Math.min(signal.length - 1, centerIndex + radius);

        for (int i = start; i < end; i++) {
            double discontinuity = Math.abs(signal[i] - signal[i - 1]);
            maxDiscontinuity = Math.max(maxDiscontinuity, discontinuity);
        }

        return maxDiscontinuity;
    }
}