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

        // The processing block size must align with the kernel switching period.
        int blockSize = periodSamples;

        // The FFT size must be large enough to avoid time-domain aliasing.
        int fftSize = CommonUtil.nextPowerOfTwo(blockSize + kernelLength - 1);

        int resultLength = signal.length + kernelLength - 1;
        double[] result = new double[Math.max(resultLength, signal.length)];

        // Pre-compute the FFT of all kernels, padded to the chosen fftSize.
        List<Complex[]> kernelTransforms = kernels.stream()
                .map(k -> SignalTransformer.fft(SignalTransformer.pad(k, fftSize)))
                .toList();

        // OLS requires an initial overlap of (kernelLength - 1). We simulate this by
        // padding the signal with zeros at the start and adding enough padding at the
        // end to accommodate the final block.
        double[] paddedSignal = SignalTransformer.pad(signal, kernelLength - 1, fftSize);

        int totalBlocks = (resultLength + blockSize - 1) / blockSize;

        // Process the signal block by block in a single OLS loop.
        for (int blockIndex = 0; blockIndex < totalBlocks; blockIndex++) {
            int outputStartIndex = blockIndex * blockSize;

            // Determine which kernel to use for this block based on the output sample index.
            int kernelIndex = (outputStartIndex / periodSamples) % kernels.size();
            Complex[] kernelTransform = kernelTransforms.get(kernelIndex);

            // Extract the K-sample input block from the appropriately padded signal.
            int inputStartIndex = blockIndex * blockSize;
            double[] inputBlock = new double[fftSize];
            int copyLength = Math.min(fftSize, paddedSignal.length - inputStartIndex);
            if (copyLength > 0) {
                System.arraycopy(paddedSignal, inputStartIndex, inputBlock, 0, copyLength);
            }

            // Perform convolution for the block in the frequency domain.
            Complex[] inputTransform = SignalTransformer.fft(inputBlock);
            Complex[] convolutionTransform = SignalTransformer.multiply(inputTransform, kernelTransform);
            double[] blockResult = SignalTransformer.ifft(convolutionTransform);

            // Copy the valid portion of the result to the output buffer.
            // The first (kernelLength - 1) samples are discarded due to circular
            // convolution artifacts. The next 'blockSize' samples are the valid result.
            int validLength = Math.min(blockSize, result.length - outputStartIndex);
            if (validLength > 0) {
                System.arraycopy(
                        blockResult,          // source array
                        kernelLength - 1,     // source position (skipping aliased samples)
                        result,               // destination array
                        outputStartIndex,     // destination position
                        validLength);         // length to copy
            }
        }

        // Ensure the final result array has the precise, correct length.
        if (result.length == resultLength) {
            return result;
        } else {
            double[] finalResult = new double[resultLength];
            System.arraycopy(result, 0, finalResult, 0, resultLength);
            return finalResult;
        }
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
        int totalBlocks = (resultLength + blockSize - 1) / blockSize;
        final int lastInputReadPosition = (totalBlocks - 1) * blockSize;
        final int requiredPaddedLength = lastInputReadPosition + fftSize;
        final double[] paddedSignal = new double[requiredPaddedLength];
        System.arraycopy(signal, 0, paddedSignal, kernelLength - 1, signal.length);

        // Process blocks
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