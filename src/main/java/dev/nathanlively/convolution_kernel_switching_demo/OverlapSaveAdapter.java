package dev.nathanlively.convolution_kernel_switching_demo;

import org.apache.arrow.memory.util.CommonUtil;
import org.apache.commons.numbers.complex.Complex;

import java.util.List;

public class OverlapSaveAdapter implements Convolution {
    @Override
    public double[] with(double[] signal, List<double[]> kernels, int periodSamples) {
        if (kernels.isEmpty()) {
            throw new IllegalArgumentException("kernels cannot be empty");
        }
        int kernelLength = kernels.getFirst().length;
        if (kernels.stream().anyMatch(kernel -> kernel.length != kernelLength)) {
            throw new IllegalArgumentException("all kernels must have the same length");
        }
        return with(signal, kernels.getFirst());
    }

    @Override
    public double[] with(double[] signal, double[] kernel) {
        SignalTransformer.validate(signal, kernel);

        int kernelLength = kernel.length;
        int fftSize = calculateOptimalFftSize(signal.length, kernelLength);
        int blockSize = fftSize - kernelLength + 1;
        int blockStartIndex = kernelLength - 1;
        int resultLength = signal.length + kernelLength - 1;

        // Pre-compute kernel FFT (zero-padded to FFT size)
        double[] paddedKernel = SignalTransformer.pad(kernel, fftSize);
        Complex[] kernelTransform = SignalTransformer.fft(paddedKernel);

        // Pre-allocate a result array
        double[] result = new double[resultLength];

        // Create a padded signal with initial zeros for overlap
        double[] paddedSignal = SignalTransformer.pad(
                signal,
                blockStartIndex, // startPaddingAmount
                resultLength - signal.length - blockStartIndex  // endPaddingAmount
        );

        // Process blocks
        int totalBlocks = (signal.length + blockSize - 1) / blockSize;
        for (int blockIndex = 0; blockIndex < totalBlocks; blockIndex++) {
            int nextBlockStartIndex = blockIndex * blockSize;

            // Extract block with proper overlap handling
            double[] block = extractSignalBlock(paddedSignal, nextBlockStartIndex, fftSize);

            // Convolve block in a frequency domain
            Complex[] blockTransform = SignalTransformer.fft(block);
            Complex[] convolutionTransform = SignalTransformer.multiply(blockTransform, kernelTransform);
            double[] blockResult = SignalTransformer.ifft(convolutionTransform);

            // Extract a valid portion (discard first kernelLength-1 samples due to aliasing)
            int validLength = Math.min(blockSize, resultLength - nextBlockStartIndex);

            if (validLength > 0) {
                System.arraycopy(
                        blockResult,  // Source object
                        blockStartIndex,  // Source index
                        result,  // Destination object
                        nextBlockStartIndex,  // Destination index
                        validLength);  // Source length to copy
            }
        }

        return result;
    }

    private double[] extractSignalBlock(double[] paddedSignal, int nextBlockStartIndex, int fftSize) {
        double[] block = new double[fftSize];  // always return FFT size
        int copyLength = Math.min(fftSize, paddedSignal.length - nextBlockStartIndex);  // handle the end of the signal where we might have less than FFT size

        if (copyLength > 0) {
            System.arraycopy(
                    paddedSignal,  // Source object
                    nextBlockStartIndex,  // Source index
                    block,  // Destination object
                    0,  // Destination index
                    copyLength  // Source length
            );
        }

        return block;
    }

    int calculateOptimalFftSize(int signalLength, int kernelLength) {
        // Minimum size needed for linear convolution without aliasing
        int minSize = 2 * kernelLength - 1;

        // For very small kernels, use a reasonable minimum
        if (minSize < 64) {
            minSize = 64;
        }

        // Find the next power of 2 that's at least minSize
        int optimalSize = CommonUtil.nextPowerOfTwo(minSize);

        // For larger signals, consider efficiency trade-offs
        // Larger FFT sizes reduce the number of blocks but increase per-block cost
        int totalConvolutionLength = signalLength + kernelLength - 1;

        // If the signal is much larger than the kernel, try larger FFT sizes
        if (signalLength > 10 * kernelLength) {
            // Calculate efficiency for different FFT sizes
            int bestSize = optimalSize;
            double bestEfficiency = calculateEfficiency(totalConvolutionLength, kernelLength, optimalSize);

            // Try powers of 2 up to a reasonable maximum
            for (int size = optimalSize * 2; size <= Math.min(optimalSize * 4, totalConvolutionLength); size *= 2) {
                double efficiency = calculateEfficiency(totalConvolutionLength, kernelLength, size);
                if (efficiency > bestEfficiency) {
                    bestSize = size;
                    bestEfficiency = efficiency;
                } else {
                    break; // Efficiency is decreasing, stop searching
                }
            }

            return bestSize;
        }

        return optimalSize;
    }

    private double calculateEfficiency(int totalLength, int kernelLength, int fftSize) {
        int blockSize = fftSize - kernelLength + 1;
        int numBlocks = (totalLength + blockSize - 1) / blockSize;
        double operationsPerSample = (numBlocks * fftSize * Math.log(fftSize)) / totalLength;
        return 1.0 / operationsPerSample; // Higher is better
    }
}