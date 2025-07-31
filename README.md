# Convolution Kernel Switching Demo

A Java demonstration project showcasing frequency domain convolution using the overlap-save method with sample-accurate kernel switching.

## Overview

This project implements a convolution system that can dynamically switch between different filter kernels during signal processing. This capability is essential for adaptive audio systems like noise-cancelling headphones, where filter characteristics must change in real-time as the acoustic environment evolves.

## The Problem

Traditional convolution libraries process signals with a fixed kernel throughout the entire operation. When you need to change filter characteristics mid-stream, you typically must:
- Stop processing
- Restart with the new kernel
- Lose any state or introduce discontinuities

This approach doesn't work for real-time adaptive systems that need smooth, sample-accurate transitions between filter responses.

## Solution

This implementation extends the overlap-save method to support:
- **Sample-accurate kernel switching** at user-defined intervals
- **Mathematically correct convolution** even across kernel boundaries  
- **Efficient frequency-domain processing** for larger kernels
- **Cyclic kernel patterns** for repeating filter sequences

## Key Features

- Implements both single-kernel and multi-kernel convolution
- Handles kernels of varying lengths (automatically padded to match)
- Preserves mathematical correctness at kernel transition boundaries
- Optimized FFT-based processing using Apache Commons Math
- Comprehensive test suite demonstrating various scenarios
- Audio file I/O support for testing with real audio signals

## Getting Started

### Prerequisites

- Java 24
- Maven 3.6+

### Running the Project

```bash
# Clone the repository
git clone https://github.com/LiveNathan/convolution-kernel-switching-demo.git
cd convolution-kernel-switching-demo

# Run tests to see the functionality in action
mvn test

# Run the Spring Boot application
mvn spring-boot:run
```

### Basic Usage

```java
// Single kernel convolution
Convolution convolution = new OverlapSaveAdapter();
double[] signal = {1.0, 2.0, 3.0, 4.0};
double[] kernel = {0.5, 0.25};
double[] result = convolution.with(signal, kernel);

// Multi-kernel convolution with switching
double[] lowpass = {0.25, 0.5, 0.25};
double[] highpass = {-0.25, 0.5, -0.25};
List<double[]> kernels = List.of(lowpass, highpass);

// Switch kernels every 1000 samples
int periodSamples = 1000;
double[] switchingResult = convolution.with(signal, kernels, periodSamples);
```

## Architecture

The project follows clean architecture principles with clear separation of concerns:

### Core Interfaces
- `Convolution` - Main convolution interface supporting both single and multi-kernel operations

### Implementation Classes
- `OverlapSaveAdapter` - Implements overlap-save algorithm with kernel switching logic
- `SignalTransformer` - Handles FFT operations and signal processing utilities
- `WavFileReader` / `WavFileWriter` - Audio file I/O for testing with real signals

### Key Design Decisions

**Block-based Processing**: Kernel switches occur at block boundaries, preserving convolution mathematics while enabling sample-accurate timing.

**FFT Optimization**: Uses frequency-domain processing for efficiency, with automatic FFT size optimization based on signal and kernel characteristics.

**Padding Strategy**: Automatically handles kernels of different lengths by padding to a common size.

## Testing

The project includes comprehensive tests covering:

- Basic convolution properties (commutativity, identity)
- Kernel switching at various boundaries
- Edge cases (empty kernels, mismatched lengths)
- Real audio file processing scenarios

Run the audio tests to hear kernel switching in action:

```bash
mvn test -Dtest=OverlapSaveAdapterTest#testConvolutionWithAudioFiles
```

This generates audio files in `target/test-outputs/` demonstrating:
- Single kernel convolution
- Multi-kernel convolution with switching every 2 seconds

## Technical Details

### Overlap-Save Method

The overlap-save method processes signals in blocks:
1. Pad signal blocks to prevent circular convolution artifacts
2. Transform signal and kernel to frequency domain (FFT)
3. Multiply frequency responses
4. Transform back to time domain (IFFT)
5. Extract valid samples, discarding overlap regions

### Kernel Switching Logic

For multi-kernel convolution:
1. Divide signal into periods based on `periodSamples`
2. Select appropriate kernel for each period using modular arithmetic
3. Process each block with its corresponding kernel transform
4. Combine results while maintaining proper sample alignment

### Performance Considerations

- Pre-computes FFTs for all kernels to avoid redundant calculations
- Uses power-of-2 FFT sizes for optimal performance
- Automatically selects FFT block sizes based on signal/kernel characteristics
- Reuses FFT instances via ThreadLocal caching

## Learn More

Read the detailed blog post about the implementation: [Sample-Accurate Kernel Switching with Overlap-Save Convolution](https://open.substack.com/pub/nathanlively/p/sample-accurate-kernel-switching-overlap-savehtml?r=hhqf8&utm_campaign=post&utm_medium=web&showWelcomeOnShare=true)

## Dependencies

- **Spring Boot 3.5.3** - Application framework
- **Apache Commons Math 4.0-beta1** - FFT operations
- **Apache Arrow 18.3.0** - Utility functions
- **JSpecify 1.0.0** - Null safety annotations

## License

This project is available under the MIT License.

## Contributing

Contributions are welcome! Please ensure all tests pass and follow the existing code style:

```bash
mvn test
```

The project uses ErrorProne and NullAway for static analysis to maintain code quality.
