# Trapper - Color Trapping System for Offset Lithography

A Java application that performs color trapping (also known as "choking" and "spreading") for multi-color offset lithography printing to compensate for misregistration between printing plates.

## Overview

Trapper reads Photoshop PSD files, separates colors into individual layers, and applies trapping by expanding lighter colors under darker colors. This prevents white gaps from appearing when printing plates are slightly misaligned.

## Features

- **Color Separation**: Automatically separates images into distinct color layers sorted by lightness
- **Intelligent Trapping**: Applies morphological dilation with linear interpolation from lightest to darkest
- **Configurable Trap Sizes**: Supports both fractional (`1/32`, `1/64`) and decimal inch specifications
- **Invisible Layer Preservation**: Preserves hidden layers from input PSD, placing them above color-separated layers
- **White Layer Optimization**: Skips trapping computation for white base layers
- **PackBits RLE Compression**: Reduces output file sizes by 96%+ (827 MB → 28 MB)
- **Parallel Processing**: Multi-threaded layer processing for 3.2x speedup
- **Built-in Verification**: Ensures trapped output matches original when flattened
- **DPI-Aware Calculations**: Precise trap sizes based on image resolution

## Requirements

- Java 21+
- Gradle 8.5+

## Building

```bash
./gradlew build
```

## Usage

### Basic Usage (Default Trap Range: 0 to 1/32")

```bash
./gradlew runColorSeparator -PpsdFile=input.psd
```

### Custom Trap Range with Fractions

```bash
java -cp build/libs/trapper.jar org.electrosaur.trapper.PsdColorSeparator input.psd 0 1/64
```

### Custom Trap Range with Decimals

```bash
java -cp build/libs/trapper.jar org.electrosaur.trapper.PsdColorSeparator input.psd 0.015625 0.03125
```

### Command-Line Arguments

```
Usage: java PsdColorSeparator <input.psd> [minTrap] [maxTrap]
  minTrap: minimum trap size (darkest layer, default: 0)
  maxTrap: maximum trap size (lightest layer, default: 1/32)

Trap sizes can be specified as:
  - Fractions: 1/32, 1/64, 1/16, etc.
  - Decimals: 0.03125, 0.015625, etc.
```

## How It Works

1. **Read Invisible Layers**: Detects and preserves hidden layers from input PSD
2. **Read PSD**: Loads multi-layer PSD file and flattens to analyze colors
3. **Color Analysis**: Identifies distinct colors and sorts by lightness (light to dark)
4. **Mask Generation**: Creates masks showing where darker colors will cover lighter colors
5. **Layer Creation**: Separates each color into its own layer
6. **Trapping**: Expands lighter colors into areas covered by darker colors using iterative dilation
7. **Layer Combination**: Merges invisible layers (top) with color-separated layers (bottom)
8. **Compression**: Applies PackBits RLE compression to reduce file size
9. **Verification**: Flattens trapped layers and compares to original to ensure correctness
10. **Output**: Writes color-separated PSD with trapped layers and preserved invisible layers

### Trap Size Interpolation

- **Lightest layer**: Maximum trap size (e.g., 1/32" = ~94 pixels at 3000 DPI)
- **Darkest layer**: Minimum trap size (e.g., 0" = defines edges)
- **Middle layers**: Linear interpolation between min and max

### Example

At 3000 DPI with default range (0 to 1/32"):
- Layer 1 (white): 0 pixels (no trapping needed - optimization)
- Layer 2 (lightest color): 94 pixels (0.0313")
- Layer 3: 71 pixels (0.0237")
- Layer 4: 47 pixels (0.0157")
- Layer 5: 24 pixels (0.0080")
- Layer 6 (darkest): 0 pixels (defines edges)

### Invisible Layer Preservation

Trapper automatically preserves hidden (invisible) layers from the input PSD file:

**Input Layer Detection:**
- Reads all layers from input PSD file
- Identifies invisible layers using visibility flag (bit 1 in layer flags byte)
- Reads image data only for invisible layers to optimize memory usage
- Skips visible layers as they're already included in the flattened composite

**Output Layer Organization:**
1. **Invisible layers** (from input) - appear first, maintaining original order
2. **Color-separated layers** (generated) - sorted lightest to darkest

**Use Cases:**
- **Design guides**: Keep ruler guides, crop marks, or registration marks hidden
- **Reference layers**: Preserve original artwork or notes for comparison
- **Workflow layers**: Maintain layer organization from design tools
- **Client notes**: Keep feedback or annotation layers without affecting trapping

**Example Output:**
```
Total layers in output: 7 (2 invisible + 5 color-separated)
  - Layer 1: "Registration marks" (invisible)
  - Layer 2: "Design notes" (invisible)
  - Layer 3: White (visible, no trap)
  - Layer 4: Yellow (visible, 94px trap)
  - Layer 5: Red (visible, 47px trap)
  - Layer 6: Blue (visible, 24px trap)
  - Layer 7: Black (visible, 0px trap - defines edges)
```

## Performance

Results from 5700×3900 pixel image at 3000 DPI:

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
│   ├── PsdColorSeparator.java    # Main trapping engine
│   ├── TestFileGenerator.java    # Generates test PSD files
│   └── App.java                   # Simple multi-layer PSD demo
└── test/
    ├── java/org/electrosaur/trapper/
    │   ├── PsdColorSeparatorTest.java      # Unit tests (trap size parsing)
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

- **PSD Layer Reading**: Manual parsing of PSD file structure to extract individual layers with metadata
- **Morphological Dilation**: Iterative 4-connected neighbor expansion
- **Masked Expansion**: Only expands into areas that will be covered by darker colors
- **Parallel Processing**: Each layer processed independently using thread pool
- **RLE Compression**: PackBits algorithm reduces file size significantly (both reading and writing)

### File Format

**Input PSD Reading:**
- Parses PSD file header and layer structure manually using RandomAccessFile
- Reads layer records with visibility flags, names, positions, and channel information
- Supports both raw (compression mode 0) and RLE (compression mode 1) layer data
- Decompresses PackBits RLE-encoded channels on the fly

**Output PSD Writing:**
Output PSD files contain:
- File header with dimensions and color mode
- Layer records with metadata (position, blend mode, opacity, visibility flags)
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
- [ ] GUI interface for parameter adjustment
- [ ] Batch processing support
- [ ] Preview generation

## License

This project is licensed under the [specify license].

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
