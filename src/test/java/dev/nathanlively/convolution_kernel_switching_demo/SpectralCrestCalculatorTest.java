package dev.nathanlively.convolution_kernel_switching_demo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;

class SpectralCrestCalculatorTest {

    private SpectralCrestCalculator calculator;
    private AudioTestHelper audioTestHelper;

    @BeforeEach
    void setUp() {
        calculator = new SpectralCrestCalculator();
        audioTestHelper = new AudioTestHelper();
    }

    @Test
    void givenEmptySpectrum_whenCalculateSpectralCrest_thenReturnZero() {
        double[] emptySpectrum = {};

        double actual = calculator.calculateCrest(emptySpectrum);

        assertThat(actual).isEqualTo(0.0);
    }

    @Test
    void givenAllZerosSpectrum_whenCalculateSpectralCrest_thenReturnZero() {
        double[] zerosSpectrum = new double[100];

        double actual = calculator.calculateCrest(zerosSpectrum);

        assertThat(actual).isEqualTo(0.0);
    }

    @Test
    void givenSinglePeakSpectrum_whenCalculateSpectralCrest_thenReturnExpectedValue() {
        double[] singlePeak = {0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0};

        double actual = calculator.calculateCrest(singlePeak);

        assertThat(actual).isCloseTo(7.0, offset(0.1));
    }

    @Test
    void givenWhiteNoise_whenCalculateSpectralCrest_thenReturnExpectedValue() {
        double[] noise = new AudioSignalBuilder()
                .withLength(1024)
                .withWhiteNoise(0.5)
                .withRandom(new Random(42))
                .build();

        double[] powerSpectrum = SignalTransformer.powerSpectrum(noise);

        double actual = calculator.calculateCrest(powerSpectrum);

        assertThat(actual).isCloseTo(8.7, offset(1.0));
    }

    @Test
    void givenPureTone_whenCalculateSpectralCrest_thenReturnHighValue() {
        double[] sine = new AudioSignalBuilder()
                .withLength(1024)
                .withSampleRate(44100)
                .withSineWave(440, 1.0)
                .build();

        double[] powerSpectrum = SignalTransformer.powerSpectrum(sine);

        double actual = calculator.calculateCrest(powerSpectrum);

        assertThat(actual).isGreaterThan(388.0);
    }

    @Test
    void givenElectronicDanceMusic_whenCalculateSpectralCrest_thenReturnExpectedValue() {
        String fileName = "you-cant-hide-6s.wav";
        WavFile testAudio = audioTestHelper.loadFromClasspath(fileName);
        double[] powerSpectrum = SignalTransformer.powerSpectrum(testAudio.signal());

        double actual = calculator.calculateCrest(powerSpectrum);

        assertThat(actual).isCloseTo(6601, offset(50.0));
    }

    @Test
    void givenSpeech_whenCalculateSpectralCrest_thenReturnExpectedValue() {
        String fileName = "Lecture5sec.wav";
        WavFile testAudio = audioTestHelper.loadFromClasspath(fileName);
        double[] powerSpectrum = SignalTransformer.powerSpectrum(testAudio.signal());

        double actual = calculator.calculateCrest(powerSpectrum);

        assertThat(actual).isCloseTo(410, offset(20.0));
    }

    @Test
    void givenAmbientMusic_whenCalculateSpectralCrest_thenReturnExpectedValue() {
        String fileName = "ambient6s.wav";
        WavFile testAudio = audioTestHelper.loadFromClasspath(fileName);
        double[] powerSpectrum = SignalTransformer.powerSpectrum(testAudio.signal());

        double actual = calculator.calculateCrest(powerSpectrum);

        assertThat(actual).isCloseTo(13541, offset(30.0));
    }

    @Test
    void givenAcousticMusic_whenCalculateSpectralCrest_thenReturnExpectedValue() {
        String fileName = "daises.wav";
        WavFile testAudio = audioTestHelper.loadFromClasspath(fileName);
        double[] powerSpectrum = SignalTransformer.powerSpectrum(testAudio.signal());

        double actual = calculator.calculateCrest(powerSpectrum);

        assertThat(actual).isCloseTo(7805, offset(50.0));
    }

    @Test
    void givenJungleMusic_whenCalculateSpectralCrest_thenReturnExpectedValue() {
        String fileName = "crossing.wav";
        WavFile testAudio = audioTestHelper.loadFromClasspath(fileName);
        double[] powerSpectrum = SignalTransformer.powerSpectrum(testAudio.signal());

        double actual = calculator.calculateCrest(powerSpectrum);

        assertThat(actual).isCloseTo(10390, offset(50.0));
    }

}