package dev.nathanlively.convolution_kernel_switching_demo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;

class MaskingFactorCalculatorTest {
    private static final Logger log = LoggerFactory.getLogger(MaskingFactorCalculatorTest.class);
    private static final int SAMPLE_RATE = 44100;

    private MaskingFactorCalculator calculator;
    private AudioTestHelper audioHelper;

    @BeforeEach
    void setUp() {
        calculator = new MaskingFactorCalculator();
        audioHelper = new AudioTestHelper();
    }

    @Test
    void givenPureTone_whenCalculateMaskingFactor_thenReturnMinimalMasking() {
        double[] sine = new AudioSignalBuilder()
                .withLength(512)
                .withSampleRate(SAMPLE_RATE)
                .withSineWave(200, 0.8)
                .build();
        double[] powerSpectrum = SignalTransformer.powerSpectrum(sine);

        double actual = calculator.calculateMaskingFactor(powerSpectrum);

        // Pure tones have low flatness but not zero, so masking factor is around 1.9
        assertThat(actual)
                .as( "Pure tone masking factor should be close to 1.0")
                .isCloseTo(1.0, offset(0.1));
    }

    @Test
    void givenWhiteNoise_whenCalculateMaskingFactor_thenReturnMaximalMasking() {
        double[] noise = new AudioSignalBuilder()
                .withLength(512)
                .withSampleRate(SAMPLE_RATE)
                .withWhiteNoise(0.8)
                .withRandom(new Random(42))
                .build();
        double[] powerSpectrum = SignalTransformer.powerSpectrum(noise);

        double actual = calculator.calculateMaskingFactor(powerSpectrum);

        // White noise should provide maximum masking (close to 3.0)
        assertThat(actual).isCloseTo(3.0, offset(0.1));
    }

    @Test
    void givenSpeech_whenCalculateMaskingFactor_thenReturnModerateValue() {
        String fileName = "Lecture5sec.wav";
        WavFile testAudio = audioHelper.loadFromClasspath(fileName);
        double[] powerSpectrum = SignalTransformer.powerSpectrum(testAudio.signal());

        double actual = calculator.calculateMaskingFactor(powerSpectrum);

        // Speech should provide moderate masking
        assertThat(actual)
                .as("Speech masking factor should be close to 2.0")
                .isBetween(1.8, 2.2);
    }

    @Test
    void givenAmbientMusic_whenCalculateMaskingFactor_thenReturnAppropriateValue() {
        String fileName = "ambient6s.wav";
        WavFile testAudio = audioHelper.loadFromClasspath(fileName);
        double[] powerSpectrum = SignalTransformer.powerSpectrum(testAudio.signal());

        double actual = calculator.calculateMaskingFactor(powerSpectrum);

        // Ambient music masking depends on spectral content
        assertThat(actual)
                .as("Ambient music masking factor should be close to 1.5")
                .isBetween(1.5, 2.8);
    }

    @Test
    void givenJungleMusic_whenCalculateMaskingFactor_thenReturnAppropriateValue() {
        String fileName = "crossing.wav";
        WavFile testAudio = audioHelper.loadFromClasspath(fileName);
        double[] powerSpectrum = SignalTransformer.powerSpectrum(testAudio.signal());

        double actual = calculator.calculateMaskingFactor(powerSpectrum);

        assertThat(actual)
                .as("Jungle music masking factor should be close to 3.0")
                .isBetween(2.5, 3.0);
    }

    @Test
    void demonstrateAllAudioTypes() {
        String[] fileNames = {
                "you-cant-hide-6s.wav", "Lecture5sec.wav", "ambient6s.wav",
                "daises.wav", "crossing.wav"
        };

        String[] labels = {
                "EDM", "Speech", "Ambient", "Acoustic", "Jungle"
        };

        log.info("=== MASKING FACTOR ANALYSIS ===");

        for (int i = 0; i < fileNames.length; i++) {
            try {
                WavFile audio = audioHelper.loadFromClasspath(fileNames[i]);
                double[] powerSpectrum = SignalTransformer.powerSpectrum(audio.signal());

                double standard = calculator.calculateMaskingFactor(powerSpectrum);
                double perceptual = calculator.calculateMaskingFactorPerceptual(powerSpectrum);

                log.info("{}: Standard={}, Perceptual={}",
                        labels[i], standard, perceptual);

            } catch (Exception e) {
                log.warn("Could not process {}: {}", fileNames[i], e.getMessage());
            }
        }
    }


}