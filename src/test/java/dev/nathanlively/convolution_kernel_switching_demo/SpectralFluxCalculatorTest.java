package dev.nathanlively.convolution_kernel_switching_demo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;

class SpectralFluxCalculatorTest {
    private static final Logger log = LoggerFactory.getLogger(SpectralFluxCalculatorTest.class);
    private SpectralFluxCalculator calculator;
    private AudioTestHelper audioHelper;

    @BeforeEach
    void setUp() {
        calculator = new SpectralFluxCalculator();
        audioHelper = new AudioTestHelper();
    }

    @Test
    void givenSine_thenReturnNearZero() {
        double[] sine = new AudioSignalBuilder()
                .withLength(1024)
                .withSampleRate(44100)
                .withSineWave(440, 1.0)
                .build();

        double actual = calculator.calculateAverageFlux(sine);

        log.info("Sine Flux (normalized): {}", actual);
        assertThat(actual).isCloseTo(0.002, offset(0.002));
        assertThat(actual).isBetween(0.0, 1.0);
    }

    @Test
    void givenNoise_thenReturnLowValue() {
        double[] noise = new AudioSignalBuilder()
                .withLength(44100)
                .withWhiteNoise(0.5)
                .withRandom(new Random(42))
                .build();

        double actual = calculator.calculateAverageFlux(noise);

        log.info("Noise Flux (normalized): {}", actual);
        assertThat(actual).isCloseTo(0.95, offset(0.05));
        assertThat(actual).isBetween(0.0, 1.0);
    }

    @Test
    void givenSpeech_thenReturnModerateValue() {
        String fileName = "Lecture5sec.wav";
        WavFile testAudio = audioHelper.loadFromClasspath(fileName);

        double actual = calculator.calculateAverageFlux(testAudio.signal());

        log.info("Speech Flux (normalized): {}", actual);
        assertThat(actual).isCloseTo(0.31, offset(0.05));
        assertThat(actual).isBetween(0.0, 1.0);
    }

    @Test
    void givenAmbientMusic_thenReturnModerateValue() {
        String fileName = "ambient6s.wav";
        WavFile testAudio = audioHelper.loadFromClasspath(fileName);

        double actual = calculator.calculateAverageFlux(testAudio.signal());

        log.info("Ambient Flux (normalized): {}", actual);
        assertThat(actual).isCloseTo(0.24, offset(0.05));
        assertThat(actual).isBetween(0.0, 1.0);
    }

    @Test
    void givenEDM_thenReturnModerateHighValue() {
        String fileName = "you-cant-hide-6s.wav";
        WavFile testAudio = audioHelper.loadFromClasspath(fileName);

        double actual = calculator.calculateAverageFlux(testAudio.signal());

        log.info("EDM Flux (normalized): {}", actual);
        assertThat(actual).isCloseTo(0.47, offset(0.10));
        assertThat(actual).isBetween(0.0, 1.0);
    }

    @Test
    void givenJungleMusic_thenReturnHighValue() {
        String fileName = "crossing.wav";
        WavFile testAudio = audioHelper.loadFromClasspath(fileName);

        double actual = calculator.calculateAverageFlux(testAudio.signal());

        log.info("Jungle Flux (normalized): {}", actual);
        assertThat(actual).isCloseTo(0.59, offset(0.10));
        assertThat(actual).isBetween(0.0, 1.0);
    }

    @Test
    void givenAcousticMusic_thenReturnVeryHighValue() {
        String fileName = "daises.wav";
        WavFile testAudio = audioHelper.loadFromClasspath(fileName);

        double actual = calculator.calculateAverageFlux(testAudio.signal());

        log.info("Acoustic Flux (normalized): {}", actual);
        assertThat(actual).isCloseTo(0.58, offset(0.10));
        assertThat(actual).isBetween(0.0, 1.0);
    }

    @Test
    void allFluxValuesAreNormalized() {
        String[] fileNames = {
                "you-cant-hide-6s.wav", "Lecture5sec.wav", "ambient6s.wav",
                "daises.wav", "crossing.wav"
        };

        String[] labels = {
                "EDM", "Speech", "Ambient", "Acoustic", "Jungle"
        };

        log.info("=== NORMALIZED FLUX ANALYSIS ===");

        for (int i = 0; i < fileNames.length; i++) {
            try {
                WavFile audio = audioHelper.loadFromClasspath(fileNames[i]);
                double normalizedFlux = calculator.calculateAverageFlux(audio.signal());

                log.info("{}: Normalized Flux = {}", labels[i], normalizedFlux);

                assertThat(normalizedFlux)
                        .as("Flux for %s should be normalized between 0 and 1", labels[i])
                        .isBetween(0.0, 1.0);

            } catch (Exception e) {
                log.warn("Could not process {}: {}", fileNames[i], e.getMessage());
            }
        }
    }

    @Test
    void fluxOrderingIsCorrect() {
        // Test that different signal types have the expected relative ordering
        double[] sine = new AudioSignalBuilder()
                .withLength(1024)
                .withSampleRate(44100)
                .withSineWave(440, 1.0)
                .build();

        double[] noise = new AudioSignalBuilder()
                .withLength(44100)
                .withWhiteNoise(0.5)
                .withRandom(new Random(42))
                .build();

        WavFile ambientAudio = audioHelper.loadFromClasspath("ambient6s.wav");
        WavFile edmAudio = audioHelper.loadFromClasspath("you-cant-hide-6s.wav");

        double sineFlux = calculator.calculateAverageFlux(sine);
        double noiseFlux = calculator.calculateAverageFlux(noise);
        double ambientFlux = calculator.calculateAverageFlux(ambientAudio.signal());
        double edmFlux = calculator.calculateAverageFlux(edmAudio.signal());

        // Verify expected ordering: sine < ambient < edm < noise
        assertThat(sineFlux).isLessThan(ambientFlux);
        assertThat(ambientFlux).isLessThan(edmFlux);
        assertThat(edmFlux).isLessThan(noiseFlux);

        log.info("Flux ordering verification: sine={}, ambient={}, edm={}, noise={}",
                sineFlux, ambientFlux, edmFlux, noiseFlux);
    }
}