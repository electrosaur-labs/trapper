# Trapper - Color Trapping System for Print Production

[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](https://www.gnu.org/licenses/gpl-3.0)

A Java application that performs color trapping (also known as "choking" and "spreading") for multi-color printing to compensate for misregistration between printing plates. Supports both offset lithography and screen printing modes.

## Overview

Trapper reads Photoshop PSD files, separates colors into individual layers, and applies trapping by expanding lighter colors under darker colors. This prevents white gaps from appearing when printing plates are slightly misaligned.

## Features

- **Color Separation**: Automatically separates images into distinct color layers sorted by lightness
- **Intelligent Trapping**: Applies morphological dilation with linear interpolation from lightest to darkest
- **Configurable Trap Sizes**: Supports both fractional (`1/32`, `1/64`) and decimal inch specifications
- **White Layer Optimization**: Skips trapping computation for white base layers
- **PackBits RLE Compression**: Reduces output file sizes by 96%+ (827 MB → 28 MB)
- **Parallel Processing**: Multi-threaded layer processing for 3.2x speedup
- **Built-in Verification**: Ensures trapped output matches original when flattened
- **DPI-Aware Calculations**: Precise trap sizes based on image resolution

## Requirements

- Java 21+
- Gradle 8.5+

## Download (For Non-Technical Users)

If you don't have Java/Gradle installed or prefer a simpler option:

1. Download the latest release JAR file: `trapper-2.0-all.jar`
2. Ensure you have Java 21+ installed ([download here](https://adoptium.net/))
3. Double-click the JAR file to launch the GUI, or run:
   ```bash
   java -jar trapper-2.0-all.jar
   ```

## Building from Source

For developers who want to build from source:

```bash
./gradlew build
```

To create the standalone executable JAR:

```bash
./gradlew shadowJar
```

This creates `build/libs/trapper-2.0-all.jar` - a single file containing all dependencies.

## Usage

### GUI Mode (Recommended)

Launch the graphical user interface:

**If using the standalone JAR:**
```bash
java -jar trapper-2.0-all.jar
```

**If building from source:**
```bash
./gradlew runGUI
```

The GUI provides:
- File browser for input/output PSD selection
- Mode selection (Offset Lithography or Screen Printing)
- Trap size inputs with unit selection (points/inches/fractions)
- Progress bar and processing log
- Mode-specific defaults:
  - **Offset Lithography**: 0 to 1/32" at 300 DPI
  - **Screen Printing**: 0 to 4pt at 300-600 DPI

### Command-Line Mode

#### Basic Usage (Default Trap Range: 0 to 1/32", Offset Mode)

```bash
./gradlew runColorSeparator -PpsdFile=input.psd
```

#### With Custom Trap Range and Mode

```bash
# Offset lithography with custom range
./gradlew runColorSeparator -PpsdFile=input.psd -PminTrap=0 -PmaxTrap=1/64 -Pmode=offset

# Screen printing with point-based measurements
./gradlew runColorSeparator -PpsdFile=input.psd -PminTrap=0 -PmaxTrap=4pt -Pmode=screen
```

#### Direct Java Execution

```bash
# Offset mode (default)
java -cp build/libs/trapper.jar org.electrosaur.trapper.PsdColorSeparator input.psd 0 1/64

# Screen printing mode
java -cp build/libs/trapper.jar org.electrosaur.trapper.PsdColorSeparator input.psd 0 4pt screen
```

### Command-Line Arguments

```
Usage: java PsdColorSeparator <input.psd> [minTrap] [maxTrap] [mode]
  minTrap: minimum trap size (darkest layer, default: 0)
  maxTrap: maximum trap size (lightest layer, default: 1/32)
  mode:    printing mode - "offset" or "screen" (default: offset)

Trap sizes can be specified as:
  - Fractions: 1/32, 1/64, 1/16, etc.
  - Decimals: 0.03125, 0.015625, etc.
  - Points: 2pt, 4pt, 6pt (1 point = 1/72 inch)
```

### Printing Modes

#### Offset Lithography
- **Description**: High-precision commercial printing
- **Trap Direction**: Light spreads into dark
- **Typical Range**: 0 to 1/32" (0.03125")
- **Typical DPI**: 300 DPI
- **Use Cases**: Commercial printing, magazines, packaging

#### Screen Printing
- **Description**: Garment printing, posters, textiles
- **Trap Direction**: Dark traps over light
- **Typical Range**: 0 to 6 points (0 to 0.083")
- **Typical DPI**: 300-600 DPI
- **Use Cases**: T-shirts, posters, signage

**Note**: Both modes use identical trap calculation (light layers expand under dark layers). The difference is only in terminology and typical trap sizes.

### PSD File Information

Inspect metadata from a PSD file:

```bash
./gradlew psdInfo -PpsdFile=input.psd
```

Output includes:
- File size
- Dimensions (width × height in pixels)
- Resolution (DPI)
- Physical size (in inches)
- Number of layers
- Number of distinct colors
- Compression ratio

Example output:
```
PSD File Information
===================

File:             JethroAsMonroe-1-layers.psd
File Size:        69.40 MB

Dimensions:       5700 × 3900 pixels
Resolution:       300 DPI
Physical Size:    19.00" × 13.00"

Layers:           10
Distinct Colors:  6

Total Pixels:     22,230,000
Uncompressed:     84.80 MB (RGBA)
Compression:      1.2x (18.2% reduction)
```

## How It Works

1. **Read PSD**: Loads multi-layer PSD file and flattens to analyze colors
2. **Color Analysis**: Identifies distinct colors and sorts by lightness (light to dark)
3. **Mask Generation**: Creates masks showing where darker colors will cover lighter colors
4. **Layer Creation**: Separates each color into its own layer
5. **Trapping**: Expands lighter colors into areas covered by darker colors using iterative dilation
6. **Compression**: Applies PackBits RLE compression to reduce file size
7. **Verification**: Flattens trapped layers and compares to original to ensure correctness
8. **Output**: Writes color-separated PSD with trapped layers

### Trap Size Interpolation

- **Lightest layer**: Maximum trap size (e.g., 1/32" = ~9 pixels at 300 DPI)
- **Darkest layer**: Minimum trap size (e.g., 0" = defines edges)
- **Middle layers**: Linear interpolation between min and max

### Example

At 300 DPI with default range (0 to 1/32"):
- Layer 1 (white): 0 pixels (no trapping needed - optimization)
- Layer 2 (lightest color): 9 pixels (0.0300")
- Layer 3: 7 pixels (0.0233")
- Layer 4: 5 pixels (0.0167")
- Layer 5: 2 pixels (0.0067")
- Layer 6 (darkest): 0 pixels (defines edges)

## Performance

Results from 5700×3900 pixel image at 300 DPI:

- **File Size Reduction**: 827 MB → 28 MB (96.6% reduction with RLE compression)
- **Processing Time**: ~2.5 minutes on 16-core CPU (vs ~8 minutes single-threaded)
- **Verification**: 0 pixel differences between trapped output and original

## Testing

Run all tests:
```bash
./gradlew test
```

Run specific test suite:
```bash
./gradlew test --tests PsdColorSeparatorTest
./gradlew test --tests TrappingIntegrationTest
```

### Test Coverage

- **14 unit tests**: Trap size parsing (fractions, decimals, error cases)
- **11 integration tests**: End-to-end trapping with real images
- **2 test images**: Small PSD files (19KB-30KB) for automated testing

## Generate Test Images

```bash
java -cp build/classes/java/test:build/classes/java/main org.electrosaur.trapper.TestImageGenerator
```

This creates:
- `src/test/resources/test-simple.psd` - 100×100 four-quadrant test image
- `src/test/resources/test-complex.psd` - 80×80 overlapping shapes test image

## Why PSD Format (Not Multi-Layer TIFF)?

While Photoshop can save multi-layer files in TIFF format using TIFF tag 37724 (ImageSourceData), we use native PSD format for the following reasons:

### Photoshop TIFF Technical Overview

- **TIFF Tag 37724**: Stores layer information in Photoshop-specific format
- **Structure**: Contains flattened composite image + layer data in separate tag
- **Compatibility**: Most applications only see flattened image; only Photoshop/Affinity/Krita read layers
- **Byte Ordering Issues**: Tag 37724 can have different endianness than TIFF container

### Java Library Support Assessment

**Reading Capabilities:**
- ✅ **TwelveMonkeys ImageIO**: Can read PSD and some Photoshop TIFF metadata
- ✅ **Bio-Formats**: Can read tag 37724 for scientific imaging
- ✅ **Apache Commons Imaging**: Can extract Photoshop metadata from TIFF

**Writing Capabilities:**
- ❌ **TwelveMonkeys ImageIO**: Explicitly states multi-layer TIFF writing is "hard" (code comments)
- ❌ **Apache Commons Imaging**: No PSD layer support
- ⚠️ **iCafe**: Partial tag 37724 support, needs significant development
- ❌ **Bio-Formats**: Not designed for writing
- 💰 **Aspose.PSD**: Commercial library, unclear TIFF support

### Decision: PSD Format

**Advantages:**
1. **Well-documented format**: Adobe's PSD specification is clear and stable
2. **Proven library support**: TwelveMonkeys ImageIO reliably reads PSD
3. **Implementation control**: Custom writer gives full control over output
4. **Industry standard**: PSD is universally recognized for pre-press workflows
5. **No licensing concerns**: Open-source libraries available

**Conclusion:** No mature open-source Java library exists that can reliably write Photoshop TIFF multi-layer files. Our custom PSD implementation provides better control, reliability, and maintainability.

## Project Structure

```
src/
├── main/java/org/electrosaur/trapper/
│   ├── PsdColorSeparator.java            # Main trapping engine
│   ├── TrapperGUI.java                   # Swing GUI interface
│   ├── TrappingStrategy.java             # Strategy interface
│   ├── AbstractTrappingStrategy.java     # Base strategy implementation
│   ├── OffsetTrappingStrategy.java       # Offset lithography strategy
│   ├── ScreenPrintingTrappingStrategy.java  # Screen printing strategy
│   ├── TestFileGenerator.java            # Generates test PSD files
│   └── App.java                           # Simple multi-layer PSD demo
└── test/
    ├── java/org/electrosaur/trapper/
    │   ├── PsdColorSeparatorTest.java      # Unit tests (trap size parsing)
    │   ├── ScreenPrintingModeTest.java     # Screen printing mode tests
    │   ├── TrappingIntegrationTest.java    # Integration tests
    │   ├── TestImageGenerator.java         # Test resource generator
    │   └── AppTest.java                     # Basic PSD creation test
    └── resources/
        ├── test-simple.psd         # 100×100 test image (30KB)
        └── test-complex.psd        # 80×80 test image (19KB)
```

## Dependencies

- **TwelveMonkeys ImageIO**: Reading PSD/TIFF files
  - `imageio-tiff:3.11.0`
  - `imageio-psd:3.11.0`
  - `imageio-core:3.11.0`
- **Guava**: Utilities
- **JUnit 4**: Testing

## Technical Details

### Color Trapping Theory

In offset lithography, each color is printed from a separate plate. Slight misalignment between plates can create visible gaps. Trapping compensates by:

1. Expanding lighter colors under darker colors (spreading)
2. Reducing darker colors into lighter colors (choking)
3. Linear interpolation ensures smooth transitions between layers

### Implementation Approach

- **Morphological Dilation**: Iterative 4-connected neighbor expansion
- **Masked Expansion**: Only expands into areas that will be covered by darker colors
- **Parallel Processing**: Each layer processed independently using thread pool
- **RLE Compression**: PackBits algorithm reduces file size significantly

### File Format

Output PSD files contain:
- File header with dimensions and color mode
- Layer records with metadata (position, blend mode, opacity)
- Layer image data (RGBA channels with RLE compression)
- Flattened composite image for preview

## Limitations

- Maximum 10 distinct colors per image
- RGB color mode only
- 8 bits per channel
- No support for layer effects, adjustment layers, or smart objects
- PSD output (not TIFF with layers)

## Future Enhancements

- [ ] Support for more than 10 colors
- [ ] CMYK color mode support
- [ ] Configurable dilation algorithm (8-connected neighbors)
- [x] GUI interface for parameter adjustment (v2.0)
- [x] Multiple printing mode support (v2.0)
- [ ] Batch processing support
- [ ] Preview generation

## License

This project is licensed under the GNU General Public License v3.0 - see the [LICENSE](LICENSE) file for details.

This means you are free to use, modify, and distribute this software, but any derivative works must also be released under GPL-3.0.

## Contributing

Contributions are welcome! Please:

1. Fork the repository
2. Create a feature branch
3. Add tests for new functionality
4. Ensure all tests pass
5. Submit a pull request

## Acknowledgments

- Uses TwelveMonkeys ImageIO for PSD reading
- Inspired by traditional pre-press trapping techniques
- Built with Claude Code assistance

## References

- Adobe Photoshop File Format Specification
- TIFF 6.0 Specification
- Adobe Photoshop TIFF Technical Notes
- TwelveMonkeys ImageIO Documentation
