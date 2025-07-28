package dev.nathanlively.convolution_kernel_switching_demo;

import org.apache.arrow.memory.util.CommonUtil;
import org.apache.commons.numbers.complex.Complex;

import java.util.Arrays;
import java.util.List;

public class OverlapSaveAdapter implements Convolution {

    @Override
    public double[] with(double[] signal, double[] kernel) {
        SignalTransformer.validate(signal, kernel);
        return with(signal, List.of(kernel), Integer.MAX_VALUE);
    }

    @Override
    public double[] with(double[] signal, List<double[]> kernels, int periodSamples) {
        if (kernels.isEmpty()) {
            throw new IllegalArgumentException("kernels cannot be empty");
        }

        SignalTransformer.validate(signal, kernels.getFirst());

        if (periodSamples <= 0) {
            throw new IllegalArgumentException("periodSamples must be positive");
        }

        int kernelLength = kernels.getFirst().length;
        if (kernels.stream().anyMatch(kernel -> kernel.length != kernelLength)) {
            throw new IllegalArgumentException("all kernels must have the same length");
        }

        // Use an optimized single-kernel path when appropriate
        if (kernels.size() == 1 && periodSamples >= signal.length) {
            return convolveSingleKernelOptimized(signal, kernels.getFirst());
        }

        return convolveWithKernelSwitching(signal, kernels, periodSamples);
    }

    private double[] convolveSingleKernelOptimized(double[] signal, double[] kernel) {
        int kernelLength = kernel.length;
        int fftSize = SignalTransformer.calculateOptimalFftSize(signal.length, kernelLength);
        int blockSize = fftSize - kernelLength + 1;
        int resultLength = signal.length + kernelLength - 1;

        Complex[] kernelTransform = SignalTransformer.fft(SignalTransformer.pad(kernel, fftSize));
        double[] result = new double[resultLength];

        int totalBlocks = (resultLength + blockSize - 1) / blockSize;
        int lastInputReadPosition = (totalBlocks - 1) * blockSize;
        int requiredPaddedLength = lastInputReadPosition + fftSize;
        double[] paddedSignal = new double[requiredPaddedLength];
        System.arraycopy(signal, 0, paddedSignal, kernelLength - 1, signal.length);

        for (int blockIndex = 0; blockIndex < totalBlocks; blockIndex++) {
            int outputStartIndex = blockIndex * blockSize;
            double[] blockResult = SignalTransformer.processConvolutionBlock(
                    paddedSignal, outputStartIndex, fftSize, kernelTransform);

            int validLength = Math.min(blockSize, resultLength - outputStartIndex);
            if (validLength > 0) {
                System.arraycopy(blockResult, kernelLength - 1, result, outputStartIndex, validLength);
            }
        }

        return result;
    }

    private double[] convolveWithKernelSwitching(double[] signal, List<double[]> kernels, int periodSamples) {
        int kernelLength = kernels.getFirst().length;
        int fftSize = CommonUtil.nextPowerOfTwo(periodSamples + kernelLength - 1);
        int resultLength = signal.length + kernelLength - 1;
        double[] result = new double[Math.max(resultLength, signal.length)];

        List<Complex[]> kernelTransforms = SignalTransformer.precomputeKernelTransforms(kernels, fftSize);
        double[] paddedSignal = SignalTransformer.pad(signal, kernelLength - 1, fftSize);
        int totalBlocks = (resultLength + periodSamples - 1) / periodSamples;

        for (int blockIndex = 0; blockIndex < totalBlocks; blockIndex++) {
            int outputStartIndex = blockIndex * periodSamples;
            int kernelIndex = (outputStartIndex / periodSamples) % kernels.size();
            Complex[] kernelTransform = kernelTransforms.get(kernelIndex);

            int inputStartIndex = blockIndex * periodSamples;
            double[] blockResult = SignalTransformer.processConvolutionBlock(
                    paddedSignal, inputStartIndex, fftSize, kernelTransform);

            int validLength = Math.min(periodSamples, result.length - outputStartIndex);
            if (validLength > 0) {
                System.arraycopy(blockResult, kernelLength - 1, result, outputStartIndex, validLength);
            }
        }

        return result.length == resultLength ? result : Arrays.copyOf(result, resultLength);
    }
}