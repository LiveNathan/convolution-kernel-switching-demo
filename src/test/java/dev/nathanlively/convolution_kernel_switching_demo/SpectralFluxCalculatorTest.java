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
    void givenSine() {
        double[] sine = new AudioSignalBuilder()
                .withLength(1024)
                .withSampleRate(44100)
                .withSineWave(440, 1.0)
                .build();
        final double actual = calculator.calculateAverageFlux(sine);
        log.info("Sine Flux: {}", actual);
        assertThat(actual).isCloseTo(0.0, offset(0.1));
    }

    @Test
    void givenNoise() {
        double[] noise = new AudioSignalBuilder()
                .withLength(44100)
                .withWhiteNoise(0.5)
                .withRandom(new Random(42))
                .build();
        final double actual = calculator.calculateAverageFlux(noise);
        log.info("Noise Flux: {}", actual);
        assertThat(actual).isCloseTo(22.0, offset(1.0));
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

                double standard = calculator.calculateAverageFlux(powerSpectrum);

                log.info("{}: Standard={}", labels[i], standard);

            } catch (Exception e) {
                log.warn("Could not process {}: {}", fileNames[i], e.getMessage());
            }
        }
    }
}