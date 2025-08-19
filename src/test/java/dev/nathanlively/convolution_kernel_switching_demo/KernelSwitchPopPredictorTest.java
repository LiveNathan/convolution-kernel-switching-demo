package dev.nathanlively.convolution_kernel_switching_demo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.*;

class KernelSwitchPopPredictorTest {
    private static final int SAMPLE_RATE = 44100;
    private KernelSwitchPopPredictor predictor;
    private AudioTestHelper audioHelper;
    private SpectralFluxCalculator fluxCalc;
    private static final Logger log = LoggerFactory.getLogger(KernelSwitchPopPredictorTest.class);
    private Random random;
    private Convolution convolution;

    @BeforeEach
    void setUp() {
        predictor = new KernelSwitchPopPredictor(SAMPLE_RATE);
        audioHelper = new AudioTestHelper();
        fluxCalc = new SpectralFluxCalculator();
        random = new Random();
        convolution = new OverlapSaveAdapter();
    }

    @Test
    void predictInaudibleSwitchesAtRandomLocations() throws IOException {
        Random random = new Random(); // Fixed seed for reproducibility
        int numTests = 10;

        for (int testRun = 0; testRun < numTests; testRun++) {
            // Generate random frequency on log scale in perceptually relevant range
            double minFreq = 50.0;   // 50 Hz
            double maxFreq = 8000.0; // 8000 Hz
            double logMinFreq = Math.log(minFreq);
            double logMaxFreq = Math.log(maxFreq);
            double randomLogFreq = logMinFreq + random.nextDouble() * (logMaxFreq - logMinFreq);
            int frequency = (int) Math.round(Math.exp(randomLogFreq));

            // Get threshold for this frequency and choose gain change 80% of threshold
            double threshold = predictor.getThresholdForFrequency(frequency);
            double gainReduction = threshold * 0.8; // Should be inaudible

            // Generate signal
            double[] signal = new AudioSignalBuilder()
                    .withLengthSeconds(2.0)
                    .withSampleRate(SAMPLE_RATE)
                    .withSineWave(frequency, 1.0)
                    .build();

            // Random switch location (avoid edges)
            int switchIndex = 1000 + random.nextInt(signal.length - 2000);

            double[] kernel1 = {1.0};
            double[] kernel2 = {1.0 - gainReduction};

            // Predict audibility
            PerceptualImpact perceptualImpact = predictor.predictAudibility(signal, kernel1, kernel2, switchIndex);

            // Generate actual convolved audio for verification
            double[] convolved = new OverlapSaveAdapter().with(signal, List.of(kernel1, kernel2), switchIndex);

            // Save for human verification
            String filename = String.format("inaudible-test-%d-freq-%dHz-gain-%.3f-audit-%.3f.wav",
                    testRun, frequency, gainReduction, perceptualImpact.ratio());
            audioHelper.save(new WavFile(SAMPLE_RATE, AudioSignals.normalize(convolved)), filename);

            assertThat(perceptualImpact.isInaudible())
                    .as("Test %d: %d Hz, %.3f gain reduction should be inaudible",
                            testRun, frequency, gainReduction)
                    .isTrue();
        }
    }

    @Test
    void findActualInaudibilityThresholdForRealAudioFiles() throws IOException {
        String[] fileNames = {
                "you-cant-hide-6s.wav", "Lecture5sec.wav", "ambient6s.wav",
                "daises.wav", "crossing.wav"
        };

        String[] labels = {
                "EDM", "Speech", "Ambient", "Acoustic", "Jungle"
        };

        Random random = new Random();
        Convolution convolution = new OverlapSaveAdapter();

        log.info("=== FINDING ACTUAL INAUDIBILITY THRESHOLDS ===");

        for (int fileIndex = 0; fileIndex < fileNames.length; fileIndex++) {
            String fileName = fileNames[fileIndex];
            String label = labels[fileIndex];

            try {
                WavFile audioFile = audioHelper.loadFromClasspath(fileName);
                double[] signal = audioFile.signal();

                // Pick one random switch location for this entire file
                int minSwitchIndex = (int) (audioFile.sampleRate() * 0.5); // After 0.5 seconds
                int maxSwitchIndex = signal.length - (int) (audioFile.sampleRate() * 0.5); // Before last 0.5 seconds
                int switchIndex = minSwitchIndex + random.nextInt(maxSwitchIndex - minSwitchIndex);

                // Find the maximum gain reduction where it's still inaudible
                double gainReduction = 0.001; // Start very small
                double stepSize = 0.001;
                double maxInaudibleGainReduction = 0.0;

                double[] kernel1 = {1.0};

                PerceptualImpact perceptualImpact;
                do {
                    double[] kernel2 = {1.0 - gainReduction};
                    perceptualImpact = predictor.predictAudibility(signal, kernel1, kernel2, switchIndex);

                    if (perceptualImpact.isInaudible()) {
                        maxInaudibleGainReduction = gainReduction;
                    }

                    gainReduction += stepSize;
                } while (perceptualImpact.isInaudible());

                // Test again with the actual max inaudible value for verification
                double[] kernel2 = {1.0 - maxInaudibleGainReduction};
                PerceptualImpact finalImpact = predictor.predictAudibility(signal, kernel1, kernel2, switchIndex);
                double[] convolved = convolution.with(signal, List.of(kernel1, kernel2), switchIndex);
                double maxDiscontinuity = findMaxDiscontinuity(convolved);

                log.info("{}: switch_at={}s, max_inaudible_gain_reduction={}, max_disc={}",
                        label, switchIndex / (double) audioFile.sampleRate(),
                        maxInaudibleGainReduction, maxDiscontinuity);

                // Save the audio at the threshold
                String outputFileName = String.format("threshold-%s-gain-%.4f-switch-%.1fs.wav",
                        label.toLowerCase(), maxInaudibleGainReduction,
                        switchIndex / (double) audioFile.sampleRate());
                audioHelper.save(new WavFile(audioFile.sampleRate(), AudioSignals.normalize(convolved)), outputFileName);

                assertThat(finalImpact.isInaudible()).isTrue();
            } catch (Exception e) {
                log.warn("Could not process {}: {}", fileName, e.getMessage());
            }
        }
    }

    @Test
    void findAudibilityThresholdAtRandomLocations() throws IOException {
        int numTests = 10;

        log.info("=== FINDING AUDIBILITY THRESHOLDS AT RANDOM LOCATIONS ===");

        for (int testRun = 0; testRun < numTests; testRun++) {
            // Generate random frequency on log scale
            double minFreq = 50.0;
            double maxFreq = 8000.0;
            double logMinFreq = Math.log(minFreq);
            double logMaxFreq = Math.log(maxFreq);
            double randomLogFreq = logMinFreq + random.nextDouble() * (logMaxFreq - logMinFreq);
            int frequency = (int) Math.round(Math.exp(randomLogFreq));

            // Generate signal
            double[] signal = new AudioSignalBuilder()
                    .withLengthSeconds(2.0)
                    .withSampleRate(SAMPLE_RATE)
                    .withSineWave(frequency, 1.0)
                    .build();

            // Random switch location (avoid edges)
            int minIndex = SAMPLE_RATE / 10;  // After 0.1 seconds
            int maxIndex = signal.length - SAMPLE_RATE / 10;  // Before last 0.1 seconds
            int switchIndex = minIndex + random.nextInt(maxIndex - minIndex);

            // Binary search for the threshold where it becomes audible
            double[] kernel1 = {1.0};
            double low = 0.0;
            double high = 0.9;
            double audibilityThreshold = high;

            // Binary search with 0.001 precision
            while (high - low > 0.001) {
                double mid = (low + high) / 2;
                double[] kernel2 = {1.0 - mid};

                PerceptualImpact impact = predictor.predictAudibility(
                        signal, kernel1, kernel2, switchIndex);

                if (impact.isAudible()) {
                    audibilityThreshold = mid;
                    high = mid;
                } else {
                    low = mid;
                }
            }

            // Verify with actual convolution at the found threshold
            double[] kernel2 = {1.0 - (audibilityThreshold * 1.2)};
            PerceptualImpact finalImpact = predictor.predictAudibility(signal, kernel1, kernel2, switchIndex);

            // Generate actual audio with switching at that point
            double[] convolved = convolution.with(signal, List.of(kernel1, kernel2), switchIndex);
            double signalAtSwitch = signal[switchIndex];
            double expectedThreshold = predictor.getThresholdForFrequency(frequency);

            log.info("Test {}: freq={}Hz, switch@{}ms, signal_value={}, " +
                     "found_threshold={}, expected_threshold={}, ratio={}, level={}",
                    testRun, frequency,
                    switchIndex * 1000.0 / SAMPLE_RATE,
                    signalAtSwitch,
                    audibilityThreshold,
                    expectedThreshold,
                    finalImpact.ratio(),
                    finalImpact.level());

            // Save audio for verification
            String filename = String.format(
                    "threshold-search-%d-freq-%dHz-thresh-%.4f-signal-%.3f.wav",
                    testRun, frequency, audibilityThreshold, signalAtSwitch);
            audioHelper.save(new WavFile(SAMPLE_RATE,
                    AudioSignals.normalize(convolved)), filename);

            // The found threshold should be somewhat related to the expected threshold
            // but adjusted for the actual signal value at the switch point
            // If signal is near zero, we'd need a huge gain change to be audible
            // If signal is near peak (±1.0), the threshold should be closer to expected
            double adjustedExpectedThreshold = expectedThreshold / Math.max(0.01, Math.abs(signalAtSwitch));
            assertThat(finalImpact.isAudible()).isTrue();
            assertThat(audibilityThreshold)
                    .as("Audibility threshold should scale with signal amplitude")
                    .isCloseTo(Math.min(0.5, adjustedExpectedThreshold), within(0.2));
        }
    }

    @Test
    void findActualAudibilityThresholdForRealAudioFiles() throws IOException {
        String[] fileNames = {
                "you-cant-hide-6s.wav", "Lecture5sec.wav", "ambient6s.wav",
                "daises.wav", "crossing.wav"
        };
        String[] labels = {
                "EDM", "Speech", "Ambient", "Acoustic", "Jungle"
        };

        log.info("=== FINDING ACTUAL AUDIBILITY THRESHOLDS ===");

        for (int fileIndex = 0; fileIndex < fileNames.length; fileIndex++) {
            String fileName = fileNames[fileIndex];
            String label = labels[fileIndex];

            try {
                WavFile audioFile = audioHelper.loadFromClasspath(fileName);
                double[] signal = audioFile.signal();

                // Pick one random switch location for this entire file
                int minSwitchIndex = (int) (audioFile.sampleRate() * 3.0); // After 0.5 seconds
                int maxSwitchIndex = signal.length - (int) (audioFile.sampleRate() * 1.0);
                int switchIndex = minSwitchIndex + random.nextInt(maxSwitchIndex - minSwitchIndex);

                // Find the minimum gain reduction where it becomes audible
                double gainReduction = 0.001; // Start very small
                double stepSize = 0.001;
                double minAudibleGainReduction = 0.0;

                double[] kernel1 = {1.0};

                PerceptualImpact perceptualImpact;
                do {
                    double[] kernel2 = {1.0 - gainReduction};
                    perceptualImpact = predictor.predictAudibility(signal, kernel1, kernel2, switchIndex);

                    if (perceptualImpact.isAudible()) {
                        minAudibleGainReduction = gainReduction;
                    }

                    gainReduction += stepSize;
                } while (perceptualImpact.isInaudible() && gainReduction < 1.0);

                // Test with the actual min audible value for verification
                final double finalGainReduction = minAudibleGainReduction * 1.2;
                double[] kernel2 = {1.0 - finalGainReduction};
                PerceptualImpact finalImpact = predictor.predictAudibility(signal, kernel1, kernel2, switchIndex);
                double[] convolved = convolution.with(signal, List.of(kernel1, kernel2), switchIndex);
                double maxDiscontinuity = findMaxDiscontinuity(convolved);

                log.info("{}: switch_at={}s, min_audible_gain_reduction={}, max_disc={}, impact_ratio={}",
                        label, switchIndex / (double) audioFile.sampleRate(),
                        finalGainReduction, maxDiscontinuity, finalImpact.ratio());

                // Save the audio at the audible threshold
                String outputFileName = String.format("audible-threshold-%s-gain-%.4f-switch-%.1fs.wav",
                        label.toLowerCase(), finalGainReduction,
                        switchIndex / (double) audioFile.sampleRate());
                audioHelper.save(new WavFile(audioFile.sampleRate(), AudioSignals.normalize(convolved)), outputFileName);

//                assertThat(finalImpact.isAudible()).isTrue();
            } catch (Exception e) {
                log.warn("Could not process {}: {}", fileName, e.getMessage());
            }
        }
    }


    @Test
    void predictAudibleSwitchForLargeGainChange() {
        double[] signal = AudioSignals.generateSineWave(100, 1.0, SAMPLE_RATE);
        double[] kernel1 = {1.0};
        double[] kernel2 = {0.5}; // 50% change at low frequency

        // Switch at a peak of the sine wave
        int samplesPerCycle = SAMPLE_RATE / 100;
        int switchIndex = samplesPerCycle / 4; // Peak at 90 degrees

        PerceptualImpact audibility = predictor.predictAudibility(signal, kernel1, kernel2, switchIndex);

        assertThat(audibility.isAudible()).isTrue();
    }

    @Test
    void highFrequencyMoreTolerantToDiscontinuities() {
        double[] lowFreqSignal = AudioSignals.generateSineWave(100, 1.0, SAMPLE_RATE);
        double[] highFreqSignal = AudioSignals.generateSineWave(8000, 1.0, SAMPLE_RATE);
        double[] kernel1 = {1.0};
        double[] kernel2 = {0.9}; // 10% change

        // Switch at peaks for both signals
        int lowFreqSwitchIndex = SAMPLE_RATE / 100 / 4; // 100 Hz peak
        int highFreqSwitchIndex = SAMPLE_RATE / 8000 / 4; // 8000 Hz peak

        PerceptualImpact lowFreqAudibility = predictor.predictAudibility(lowFreqSignal, kernel1, kernel2, lowFreqSwitchIndex);
        PerceptualImpact highFreqAudibility = predictor.predictAudibility(highFreqSignal, kernel1, kernel2, highFreqSwitchIndex);

        assertThat(highFreqAudibility.ratio()).isLessThan(lowFreqAudibility.ratio());
    }

    // Multiply base threshold by these factors
//    private static final double CONTENT_MULTIPLIERS = {
//            1.0,   // Pure tone (no adjustment)
//            1.5,   // Harmonic content (musical instruments)
//            2.0,   // Speech
//            2.5,   // Ambient/textured sounds
//            3.0    // Noise/percussion
//    };

    @Test
    void givenPureTone_whenCalculateMaskingFactor_thenReturn1() {
        final double[] sineWaveSignal = new AudioSignalBuilder()
                .withLength(512)
                .withSampleRate(SAMPLE_RATE)
                .withSineWave(100, 0.8).build();
        double[] powerSpectrum = SignalTransformer.powerSpectrum(sineWaveSignal);
        double spectralFlux = fluxCalc.normalizedAverageFlux(sineWaveSignal);

        double actual = predictor.calculateMaskingFactorWithFlux(powerSpectrum, spectralFlux);

        assertThat(actual).isCloseTo(1, offset(0.01));
    }

    @Test
    void givenNoise_whenCalculateMaskingFactor_thenReturn3() {
        double[] signal = new AudioSignalBuilder()
                .withLength(512)
                .withWhiteNoise(0.5)
                .withRandom(new Random(42))
                .build();
        double[] powerSpectrum = SignalTransformer.powerSpectrum(signal);
        double spectralFlux = fluxCalc.normalizedAverageFlux(signal);

        double actual = predictor.calculateMaskingFactorWithFlux(powerSpectrum, spectralFlux);

        assertThat(actual).isCloseTo(3, offset(0.01));
    }

    @Test
    void givenJungleMusic_whenCalculateMaskingFactor_thenReturn3() {
        String fileName = "crossing.wav";
        WavFile signal = audioHelper.loadFromClasspath(fileName);
        double[] powerSpectrum = SignalTransformer.powerSpectrum(signal.signal());
        double spectralFlux = fluxCalc.normalizedAverageFlux(signal.signal());

        double actual = predictor.calculateMaskingFactorWithFlux(powerSpectrum, spectralFlux);

        assertThat(actual).isCloseTo(3, offset(0.01));
    }

    @Test
    void givenSpeech_whenCalculateMaskingFactor_thenReturn2() {
        String fileName = "Lecture5sec.wav";
        WavFile signal = audioHelper.loadFromClasspath(fileName);
        double[] powerSpectrum = SignalTransformer.powerSpectrum(signal.signal());
        double spectralFlux = fluxCalc.normalizedAverageFlux(signal.signal());

        double actual = predictor.calculateMaskingFactorWithFlux(powerSpectrum, spectralFlux);

        assertThat(actual).isCloseTo(2, offset(0.04));
    }

    @Disabled("unless we need to calibrate again")
    @Test
    void calibrateBarkBandThresholds() throws IOException {
        Random random = new Random(42);
        Convolution convolution = new OverlapSaveAdapter();

        // Access package-private BARK_CENTER_FREQUENCIES and BARK_DISCONTINUITY_THRESHOLDS
        double[] frequencies = KernelSwitchPopPredictor.BARK_CENTER_FREQUENCIES;
        double[] thresholds = KernelSwitchPopPredictor.BARK_DISCONTINUITY_THRESHOLDS;

        log.info("=== BARK BAND THRESHOLD CALIBRATION ===");
        log.info("Generating {} test files for threshold validation", frequencies.length);

        for (int i = 0; i < frequencies.length; i++) {
            double frequency = frequencies[i];
            double threshold = thresholds[i];

            // Generate 3-second sine wave at this frequency
            double[] signal = new AudioSignalBuilder()
                    .withLengthSeconds(3.0)
                    .withSampleRate(SAMPLE_RATE)
                    .withSineWave(frequency, 1.0)
                    .build();

            // Create kernels using the threshold
            double[] kernel1 = {1.0};
            double[] kernel2 = {1.0 - threshold}; // Apply the threshold as gain reduction

            // Random switching period to avoid zero-crossing alignment
            // Use 0.2-0.5 seconds for good perceptual testing
            double minPeriod = 0.2;
            double maxPeriod = 0.5;
            double randomPeriod = minPeriod + random.nextDouble() * (maxPeriod - minPeriod);
            int periodSamples = (int) (SAMPLE_RATE * randomPeriod);

            // Apply convolution with periodic kernel switching
            double[] result = convolution.with(signal, List.of(kernel1, kernel2), periodSamples);

            // Calculate actual discontinuity for reference
            double maxDiscontinuity = findMaxDiscontinuity(result);

            // Log details for analysis
            log.info("Band {}: {}Hz, threshold={}, period={}s, max_disc={}",
                    i, frequency, threshold, randomPeriod, maxDiscontinuity);

            // Save with descriptive filename
            String filename = String.format("calibrate-band-%02d-freq-%05.0fHz-thresh-%.3f-disc-%.4f.wav",
                    i, frequency, threshold, maxDiscontinuity);
            audioHelper.save(new WavFile(SAMPLE_RATE,
                    AudioSignals.normalize(result)), filename);
        }

        log.info("Calibration files generated. Listen to each file:");
        log.info("- If you hear pops/clicks, reduce that band's threshold");
        log.info("- If completely clean, threshold might be conservative (could increase)");
        log.info("- Note: bands 8-14 (1-5kHz) are most critical for perception");
    }

    private double findMaxDiscontinuity(double[] signal) {
        double maxJump = 0.0;
        for (int i = 1; i < signal.length; i++) {
            double jump = Math.abs(signal[i] - signal[i - 1]);
            maxJump = Math.max(maxJump, jump);
        }
        return maxJump;
    }
}