package dev.nathanlively.convolution_kernel_switching_demo;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.ByteArrayInputStream;
import java.nio.file.Path;

public class WavFileWriter {

    public void saveToFile(WavFile wavFile, Path outputPath) {
        double[][] channels = {wavFile.signal()};
        saveToFile(wavFile.sampleRate(), channels, outputPath);
    }

    public void saveToFile(WavFileReader.MultiChannelWavFile wavFile, Path outputPath) {
        saveToFile(wavFile.sampleRate(), wavFile.channels(), outputPath);
    }

    public void saveToFile(long sampleRate, double[][] channels, Path outputPath) {
        try {
            int channelCount = channels.length;
            int sampleCount = channels.length > 0 ? channels[0].length : 0;
            int bitDepth = 16; // Standard 16-bit depth

            AudioFormat audioFormat = new AudioFormat(
                    AudioFormat.Encoding.PCM_SIGNED,
                    sampleRate,
                    bitDepth,
                    channelCount,
                    (bitDepth / 8) * channelCount, // frame size
                    sampleRate, // frame rate
                    false // little endian
            );

            byte[] audioData = convertToByteArray(channels, bitDepth);
            ByteArrayInputStream byteStream = new ByteArrayInputStream(audioData);
            AudioInputStream audioInputStream = new AudioInputStream(byteStream, audioFormat, sampleCount);

            AudioSystem.write(audioInputStream, AudioFileFormat.Type.WAVE, outputPath.toFile());

            audioInputStream.close();
            byteStream.close();
        } catch (Exception e) {
            throw new RuntimeException("Failed to save WAV file: " + outputPath, e);
        }
    }

    public void saveToFile(long sampleRate, double[] monoSignal, Path outputPath) {
        double[][] channels = {monoSignal};
        saveToFile(sampleRate, channels, outputPath);
    }

    private byte[] convertToByteArray(double[][] channels, int bitDepth) {
        int channelCount = channels.length;
        int sampleCount = channels.length > 0 ? channels[0].length : 0;
        int bytesPerSample = bitDepth / 8;
        byte[] buffer = new byte[sampleCount * channelCount * bytesPerSample];

        double scaleFactor = Math.pow(2, bitDepth - 1) - 1;
        int bufferIndex = 0;

        for (int i = 0; i < sampleCount; i++) {
            for (double[] channel : channels) {
                double sampleValue = channel[i];
                int intValue = (int) (sampleValue * scaleFactor);

                // Clamp to valid range
                int maxValue = (int) scaleFactor;
                int minValue = -maxValue - 1;
                intValue = Math.max(minValue, Math.min(maxValue, intValue));

                for (int byteIndex = 0; byteIndex < bytesPerSample; byteIndex++) {
                    buffer[bufferIndex++] = (byte) ((intValue >>> (8 * byteIndex)) & 0xFF);
                }
            }
        }

        return buffer;
    }
}