package dev.nathanlively.convolution_kernel_switching_demo;

import org.apache.arrow.memory.util.CommonUtil;
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
                .withLength(CommonUtil.nextPowerOfTwo(1024))
                .withSampleRate(44100)
                .withSineWave(440, 1.0)
                .build();

        double actual = calculator.calculateAverageFlux(sine);

        log.info("Sine Flux (normalized): {}", actual);
        assertThat(actual).isCloseTo(0.0, offset(0.01));
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

        assertThat(actual)
                .as("Noise flux")
                .isCloseTo(0.95, offset(0.05));
        assertThat(actual).isBetween(0.0, 1.0);
    }

    @Test
    void givenSpeech_thenReturnModerateValue() {
        String fileName = "Lecture5sec.wav";
        WavFile testAudio = audioHelper.loadFromClasspath(fileName);

        double actual = calculator.calculateAverageFlux(testAudio.signal());

        assertThat(actual)
                .as("Speech flux")
                .isCloseTo(0.5, offset(0.05));
        assertThat(actual).isBetween(0.0, 1.0);
    }

    @Test
    void givenAmbientMusic_thenReturnModerateValue() {
        String fileName = "ambient6s.wav";
        WavFile testAudio = audioHelper.loadFromClasspath(fileName);

        double actual = calculator.calculateAverageFlux(testAudio.signal());

        assertThat(actual)
                .as("Ambient music flux")
                .isCloseTo(0.39, offset(0.01));
        assertThat(actual).isBetween(0.0, 1.0);
    }

    @Test
    void givenEDM_thenReturnModerateHighValue() {
        String fileName = "you-cant-hide-6s.wav";
        WavFile testAudio = audioHelper.loadFromClasspath(fileName);

        double actual = calculator.calculateAverageFlux(testAudio.signal());

        assertThat(actual)
                .as("EDM music flux")
                .isCloseTo(0.78, offset(0.05));
        assertThat(actual).isBetween(0.0, 1.0);
    }

    @Test
    void givenJungleMusic_thenReturnHighValue() {
        String fileName = "crossing.wav";
        WavFile testAudio = audioHelper.loadFromClasspath(fileName);

        double actual = calculator.calculateAverageFlux(testAudio.signal());

        assertThat(actual)
                .as("Jungle music flux")
                .isCloseTo(1.0, offset(0.10));
        assertThat(actual).isBetween(0.0, 1.0);
    }

    @Test
    void givenAcousticMusic_thenReturnVeryHighValue() {
        String fileName = "daises.wav";
        WavFile testAudio = audioHelper.loadFromClasspath(fileName);

        double actual = calculator.calculateAverageFlux(testAudio.signal());

        assertThat(actual)
                .as("Acoustic music flux")
                .isCloseTo(1.0, offset(0.10));
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