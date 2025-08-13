package dev.nathanlively.convolution_kernel_switching_demo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;

class KernelSwitchPopPredictorTest {
    private static final int SAMPLE_RATE = 44100;
    private KernelSwitchPopPredictor predictor;
    private AudioTestHelper audioHelper;
    private SpectralFluxCalculator fluxCalc;

    @BeforeEach
    void setUp() {
        predictor = new KernelSwitchPopPredictor(SAMPLE_RATE);
        audioHelper = new AudioTestHelper();
        fluxCalc = new SpectralFluxCalculator();
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
            Convolution convolution = new OverlapSaveAdapter();
            double[] convolved = convolution.with(signal, List.of(kernel1, kernel2), switchIndex);

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

}