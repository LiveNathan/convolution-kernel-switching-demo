package dev.nathanlively.convolution_kernel_switching_demo;

import org.springframework.core.io.ClassPathResource;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.File;
import java.io.IOException;

public class WavFileReader {

    public MultiChannelWavFile loadFromClasspath(String fileName) {
        try {
            String path = new ClassPathResource(fileName).getFile().getCanonicalPath();
            return loadFromFile(path);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load WAV file from classpath: " + fileName, e);
        }
    }

    public MultiChannelWavFile loadFromFile(String filePath) {
        try (AudioInputStream audioStream = AudioSystem.getAudioInputStream(new File(filePath))) {
            AudioFormat format = audioStream.getFormat();

            long sampleRate = (long) format.getSampleRate();
            int bitDepth = format.getSampleSizeInBits();
            int channelCount = format.getChannels();
            int frameSize = format.getFrameSize();

            return loadAudioData(audioStream, sampleRate, bitDepth, channelCount, frameSize);
        } catch (Exception e) {
            throw new RuntimeException("Failed to load WAV file: " + filePath, e);
        }
    }

    private MultiChannelWavFile loadAudioData(AudioInputStream audioStream, long sampleRate,
                                              int bitDepth, int channelCount, int frameSize) throws IOException {
        int bufferFrames = 8192;
        byte[] buffer = new byte[bufferFrames * frameSize];

        double[][] channels = new double[channelCount][];
        int totalSamples = 0;
        int capacity = bufferFrames;

        for (int c = 0; c < channelCount; c++) {
            channels[c] = new double[capacity];
        }

        int bytesRead;
        while ((bytesRead = audioStream.read(buffer)) != -1) {
            int framesRead = bytesRead / frameSize;

            if (totalSamples + framesRead > capacity) {
                capacity = Math.max(capacity * 2, totalSamples + framesRead);
                for (int c = 0; c < channelCount; c++) {
                    double[] newArray = new double[capacity];
                    System.arraycopy(channels[c], 0, newArray, 0, totalSamples);
                    channels[c] = newArray;
                }
            }

            convertBytesToSamples(buffer, bytesRead, channels, totalSamples, channelCount, bitDepth);
            totalSamples += framesRead;
        }

        for (int c = 0; c < channelCount; c++) {
            if (totalSamples < capacity) {
                double[] trimmed = new double[totalSamples];
                System.arraycopy(channels[c], 0, trimmed, 0, totalSamples);
                channels[c] = trimmed;
            }
        }

        return new MultiChannelWavFile(sampleRate, channels);
    }

    private void convertBytesToSamples(byte[] buffer, int bytesRead, double[][] channels,
                                       int startSample, int channelCount, int bitDepth) {
        int bytesPerSample = bitDepth / 8;
        int frameSize = bytesPerSample * channelCount;
        int frames = bytesRead / frameSize;
        double scaleFactor = Math.pow(2, bitDepth - 1) - 1;

        for (int frame = 0; frame < frames; frame++) {
            for (int channel = 0; channel < channelCount; channel++) {
                int sampleValue = extractSampleFromBytes(buffer, frame, channel, channelCount, bytesPerSample);
                channels[channel][startSample + frame] = sampleValue / scaleFactor;
            }
        }
    }

    private int extractSampleFromBytes(byte[] buffer, int frame, int channel, int channelCount, int bytesPerSample) {
        int offset = (frame * channelCount + channel) * bytesPerSample;

        if (bytesPerSample == 1) {
            return buffer[offset] - 128;
        } else if (bytesPerSample == 2) {
            int value = (buffer[offset] & 0xFF) | ((buffer[offset + 1] & 0xFF) << 8);
            if ((value & 0x8000) != 0) {
                value |= 0xFFFF0000;
            }
            return value;
        } else if (bytesPerSample == 3) {
            int value = (buffer[offset] & 0xFF) | ((buffer[offset + 1] & 0xFF) << 8)
                        | ((buffer[offset + 2] & 0xFF) << 16);
            if ((value & 0x800000) != 0) {
                value |= 0xFF000000;
            }
            return value;
        }

        throw new RuntimeException("Unsupported bit depth: " + (bytesPerSample * 8));
    }

    public record MultiChannelWavFile(long sampleRate, double[][] channels) {
        public int channelCount() {
            return channels.length;
        }

        public int length() {
            return channels.length > 0 ? channels[0].length : 0;
        }

        public double[] getChannel(int index) {
            return channels[index];
        }

        public WavFile toMono() {
            if (channels.length == 0) {
                return new WavFile(sampleRate, new double[0]);
            }
            return new WavFile(sampleRate, channels[0]);
        }
    }
}