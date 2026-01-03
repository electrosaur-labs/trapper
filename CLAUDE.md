# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Trapper is a Java application that performs color trapping (choking/spreading) for multi-color print production. It reads Photoshop PSD files, separates colors into layers, and applies morphological dilation to prevent white gaps from misregistration between printing plates.

**Key concept:** Lighter colors expand under darker colors to create overlap. This is universal across both offset lithography and screen printing modes.

### Companion Projects

**Trapper Photoshop Plugin** (`../trapper-photoshop/`): A UXP plugin that brings trapping functionality directly into Adobe Photoshop. This is the primary interface for most users (designers, small print shops) who work in Photoshop. The plugin provides seamless workflow integration - users can design, trap, and export separations without leaving Photoshop.

**This Java application** serves as:
1. **Reference implementation** for the trapping algorithms
2. **CLI tool** for batch processing and automation
3. **Standalone option** for users without Photoshop (using Affinity Photo, GIMP, etc.)
4. **Production pipeline integration** for large-scale print operations

## Color Trapping Theory

In multi-color printing, each color prints from a separate plate (offset lithography) or screen (screen printing). Slight misalignment between plates creates visible white gaps where colors should meet. Trapping compensates by expanding lighter colors under darker colors to create overlap.

**Key principle:** Light colors spread under dark colors (universal across all printing modes)

**Why this works:**
- Darker colors hide the trap (overlap is not visible)
- Prevents white gaps from misregistration
- Maintains visual appearance of edges (darker color defines the edge)

### Printing Modes

1. **Offset Lithography**
   - High-precision commercial printing
   - Typical trap range: 0 to 1/32" (0.03125")
   - Typical DPI: 300
   - Use cases: Magazines, packaging, commercial printing

2. **Screen Printing**
   - Garment printing, posters, textiles
   - Typical trap range: 0 to 4-6 points (0 to 0.056-0.083")
   - Typical DPI: 300-600
   - Use cases: T-shirts, posters, signage

**Note:** Both modes use identical trap calculation. The difference is only in terminology and typical trap sizes. The physics of trapping is universal.

### Trap Size Formats

All trap sizes can be specified in multiple formats:
- **Fractions:** `1/32`, `1/64`, `1/16` (inches)
- **Decimals:** `0.03125`, `0.015625` (inches)
- **Points:** `2pt`, `4pt`, `6pt` (72 points = 1 inch)

## Build and Test Commands

### Building
```bash
# Standard build (compiles code and runs tests)
./gradlew build

# Fast build without tests
./gradlew assemble

# Clean and rebuild
./gradlew clean build

# Create standalone executable JAR with all dependencies
./gradlew shadowJar
# Output: build/libs/trapper-2.0-all.jar
```

### Testing
```bash
# Run all tests
./gradlew test

# Run all tests with detailed output
./gradlew test --info

# Run specific test class
./gradlew test --tests PsdColorSeparatorTest
./gradlew test --tests TrappingIntegrationTest
./gradlew test --tests ScreenPrintingModeTest
./gradlew test --tests MetadataPreservationTest

# Run single test method
./gradlew test --tests TrappingIntegrationTest.testTrapping_SimpleTestImage_ProducesCorrectOutput

# Run tests continuously (watch mode)
./gradlew test --continuous
```

### Running the Application
```bash
# Launch GUI
./gradlew runGUI

# Command-line color separation (default: 0 to 1/32" offset mode)
./gradlew runColorSeparator -PpsdFile=input.psd

# With custom trap range and mode
./gradlew runColorSeparator -PpsdFile=input.psd -PminTrap=0 -PmaxTrap=1/64 -Pmode=offset
./gradlew runColorSeparator -PpsdFile=input.psd -PminTrap=0 -PmaxTrap=4pt -Pmode=screen

# PSD file information (dimensions, DPI, layers, colors, compression)
./gradlew psdInfo -PpsdFile=input.psd

# Utility tasks
./gradlew generateTestFile -PtestName=test-simple -Pwidth=100 -Pheight=100
./gradlew resizePsd -PpsdFile=input.psd -Pscale=0.1
./gradlew generateComparison -PinputFile=in.psd -PoutputFile=out.psd
./gradlew visualizeOverlaps -PpsdFile=input.psd
```

### Direct Java Execution
```bash
# Using built JAR
java -cp build/libs/trapper.jar org.electrosaur.trapper.PsdColorSeparator input.psd 0 1/64 offset
java -cp build/libs/trapper.jar org.electrosaur.trapper.PsdMetadataReader input.psd

# Standalone JAR (GUI or CLI)
java -jar build/libs/trapper-2.0-all.jar                    # GUI mode
java -jar build/libs/trapper-2.0-all.jar input.psd 0 1/32   # CLI mode
```

## Architecture

### Core Processing Pipeline

1. **PsdColorSeparator** (`PsdColorSeparator.java`) - Main engine
   - Reads PSD using TwelveMonkeys ImageIO
   - Flattens image and analyzes colors
   - Sorts colors by lightness (white → black)
   - Creates masks showing where darker colors cover lighter colors
   - Applies trapping via morphological dilation (4-connected neighbors)
   - Writes multi-layer PSD with PackBits RLE compression
   - Verifies output by flattening and comparing to original

2. **TrappingStrategy** - Strategy pattern for different printing modes
   - `TrappingStrategy` interface
   - `AbstractTrappingStrategy` - Base implementation with linear interpolation
   - `OffsetTrappingStrategy` - Offset lithography (0 to 1/32" typical)
   - `ScreenPrintingTrappingStrategy` - Screen printing (0 to 4-6pt typical)
   - **Important:** Both strategies use identical calculation (light expands under dark); only defaults differ

3. **TrapperGUI** (`TrapperGUI.java`) - Swing GUI
   - File browser for input/output selection
   - Mode selection with appropriate defaults
   - Trap size inputs with unit conversion (points/inches/fractions)
   - Progress bar and processing log

### Key Algorithms

**Color Separation:**
- Extract all unique RGB colors from flattened image
- Sort by lightness: `0.299*R + 0.587*G + 0.114*B`
- Create separate layer for each color (max 10 colors)

**Trap Calculation:**
- White base layer: 0 pixels (optimization - white doesn't trap)
- Lightest non-white layer: maximum trap size
- Darkest layer: minimum trap size (typically 0 - defines edges)
- Middle layers: linear interpolation
- Formula: `pixels = (minTrap + (maxTrap - minTrap) * normalizedPosition) * DPI`

**Trapping Process:**
- For each layer (parallel processing):
  - Create mask of areas covered by darker layers
  - Iteratively dilate layer pixels into masked areas (4-connected neighbors)
  - Stop when expansion count reached or no more pixels to expand

**File Format:**
- Custom PSD writer (not TIFF - see DECISIONS.md)
- Structure: Header → Color Mode Data → Image Resources (XMP metadata) → Layer Info → Layer Image Data → Composite Image
- Compression: PackBits RLE (96%+ reduction typical)
- Metadata: Preserves original XMP, adds processing history to `xmpMM:History`

### Parallel Processing

Each layer's trapping is computed independently using `ExecutorService` with thread pool sized to available CPU cores. This provides 3.2x speedup on multi-core systems.

**Important:** Output files differ slightly at byte level between runs due to non-deterministic thread scheduling, but pixel values are always identical (verified by flattening and comparing).

### DPI Awareness

Trap sizes are specified in physical units (inches, points, fractions) and converted to pixels based on actual image DPI:
- Extract DPI from PSD metadata (default: 72 if not present)
- Convert: `pixels = inches * DPI`
- 1 point = 1/72 inch
- Common fractions: 1/32" = 0.03125", 1/64" = 0.015625"

## Code Organization

```
src/main/java/org/electrosaur/trapper/
├── PsdColorSeparator.java              # Main trapping engine (~1500 lines)
├── TrapperGUI.java                     # Swing GUI interface (~800 lines)
├── TrappingStrategy.java               # Strategy interface
├── AbstractTrappingStrategy.java       # Base strategy with linear interpolation
├── OffsetTrappingStrategy.java         # Offset lithography defaults
├── ScreenPrintingTrappingStrategy.java # Screen printing defaults
├── PsdMetadataReader.java              # Utility: Read PSD info
├── PsdResizer.java                     # Utility: Resize PSD files
├── ComparisonImageGenerator.java       # Utility: Generate before/after comparison
├── LayerOverlapVisualizer.java         # Utility: Visualize layer overlaps
├── TestFileGenerator.java              # Generates test PSD files
└── App.java                            # Simple PSD demo

src/test/java/org/electrosaur/trapper/
├── PsdColorSeparatorTest.java          # Unit tests (trap size parsing)
├── TrappingIntegrationTest.java        # Integration tests (end-to-end)
├── ScreenPrintingModeTest.java         # Screen printing mode tests
├── MetadataPreservationTest.java       # XMP metadata preservation tests
├── TrappingVerificationTest.java       # Verification tests
├── TestImageGenerator.java             # Generates test resources
└── AppTest.java                        # Basic PSD creation test

src/test/resources/
├── test-simple.psd                     # 100×100 four-quadrant test image
└── test-complex.psd                    # 80×80 overlapping shapes test image
```

## Important Design Decisions

Reference `DECISIONS.md` for full rationale. Key points:

1. **PSD Format:** Native PSD output (not multi-layer TIFF) because no mature open-source Java library can reliably write Photoshop TIFF multi-layer files.

2. **Universal Trapping:** Both offset and screen printing modes use identical trap calculation (light expands under dark). Industry terminology varies, but physics is universal.

3. **Metadata Preservation:** Preserve original XMP metadata intact. Add processing history to `xmpMM:History` but do NOT set copyright/creator fields - images belong to their creators, not the processing software.

4. **Parallel Processing:** Layer processing is parallelized for 3.2x speedup. Accept non-deterministic byte-level output as long as pixel values are correct.

5. **White Layer Optimization:** Skip trap computation for white base layers (RGB 255,255,255) since white doesn't need trapping.

6. **PackBits Compression:** Use RLE compression for 96%+ file size reduction (827 MB → 28 MB typical).

## Testing Strategy

- **Small test images:** 100×100 pixel test files (19-30 KB) for fast execution and version control friendliness
- **Unit tests:** Trap size parsing (fractions, decimals, points, error cases)
- **Integration tests:** End-to-end trapping with real PSD files
- **Verification:** Every test flattens trapped output and compares to original (must be pixel-perfect)
- **Test images generated programmatically:** `TestImageGenerator.java` creates reproducible test PSD files

## Common Development Patterns

### Adding a New Trapping Strategy

1. Create class implementing `TrappingStrategy` interface
2. Extend `AbstractTrappingStrategy` if using linear interpolation (recommended)
3. Override `getName()`, `getDescription()`, `getTrapDirection()`
4. Optionally override `calculateExpansion()` for custom trap calculation
5. Add mode selection to `PsdColorSeparator.main()` and `TrapperGUI`

### Modifying Trap Calculation

- Core logic: `PsdColorSeparator.applyTrapping()` method
- Iterative dilation: `expandPixel()` helper method (4-connected neighbors)
- Change to 8-connected: Modify neighbor offsets in `expandPixel()`

### Adding New PSD Features

- Read: Use TwelveMonkeys ImageIO (`ImageIO.read()`)
- Write: Custom PSD writer in `PsdColorSeparator.writePsd()` method
- Reference: Adobe PSD specification for file format details
- Metadata: XMP handling in `createXMPMetadata()` method

### Syncing Changes with Photoshop Plugin

When modifying core algorithms, ensure changes are reflected in both implementations:

**Java → JavaScript porting checklist:**
1. **Color analysis**: `PsdColorSeparator.java` → `TrappingEngine.js:analyzeColors()`
2. **Trap calculation**: `AbstractTrappingStrategy.java` → `TrappingEngine.js:calculateTrapSize()`
3. **Dilation algorithm**: `PsdColorSeparator.applyTrapping()` → `TrappingEngine.js:applyDilationWithMask()`
4. **Trap size parsing**: `PsdColorSeparator.parseTrapSize()` → `TrapSizeParser.js:parse()`

**Testing both implementations:**
- Java: `./gradlew test`
- Plugin: `cd ../trapper-photoshop && npm test`
- Integration: Process same PSD in both and compare pixel-perfect output

## Dependencies

- **TwelveMonkeys ImageIO 3.11.0:** PSD/TIFF reading
  - `imageio-psd`, `imageio-tiff`, `imageio-core`
- **Guava 32.1.3-jre:** Utilities
- **JUnit 4.13.2:** Testing
- **Shadow Plugin 8.1.1:** Creates fat/uber JAR with all dependencies

## Limitations and Constraints

- Maximum 10 distinct colors per image
- RGB color mode only (no CMYK, Lab, Grayscale, Indexed)
- 8 bits per channel only (no 16/32-bit)
- No layer effects, adjustment layers, or smart objects in source PSD
- Output is PSD format only (not multi-layer TIFF)

### Why RGB Mode Only (No CMYK Support)

**This application targets spot color printing workflows**, not process color (CMYK) printing.

**RGB approach (current implementation):**
- Each distinct RGB color represents a separate printing plate/screen
- Trapping operates at the color level (entire color objects)
- Simpler model: lighter colors spread under darker colors based on overall lightness
- Ideal for: spot color printing, screen printing, simple offset jobs

**CMYK approach (not implemented):**
- CMYK trapping operates at the **channel level**, not color level
- Each channel (C, M, Y, K) represents a physical printing plate
- Trapping only applies to channels that differ between adjacent colors
- **Common ink optimization**: Colors sharing ink channels (e.g., Red=M+Y and Orange=M+Y both share Magenta) get natural registration
- Requires per-pixel CMYK comparison with neighbors and selective per-channel trapping
- Much more complex algorithm

**Implementation complexity for CMYK would require:**
1. Channel-level analysis instead of color-level separation
2. Per-pixel neighbor comparison to identify differing channels
3. Calculate common ink percentages between adjacent colors
4. Apply different trap directions/amounts per channel at same boundary
5. Handle gradients, blends, and varying ink densities

**Conclusion:** CMYK process color printing requires professional prepress software (Adobe InDesign, Esko DeskPack) with sophisticated CMYK trapping engines. This application's RGB approach is optimal for spot color work where each color is a discrete printing element.

**For users needing CMYK:** Use professional prepress tools designed for process color trapping.

## Error Handling Philosophy

Fail fast with clear error messages rather than attempting recovery. Print production errors are expensive - better to stop than produce incorrect output.

Examples:
- Invalid trap size: Show expected formats
- File read errors: Report specific file and permission issues
- DPI missing: Use 72 DPI default with warning
- Too many colors: Report limit and suggest color reduction

## Quick Reference: Common Tasks

**Development workflow:**
```bash
# Clean build and test
./gradlew clean build

# Run trapping on a file
./gradlew runColorSeparator -PpsdFile=input.psd

# Inspect file metadata
./gradlew psdInfo -PpsdFile=input.psd

# Launch GUI for interactive testing
./gradlew runGUI
```

**Debugging:**
```bash
# Generate small test file for debugging
./gradlew generateTestFile -PtestName=debug-test -Pwidth=100 -Pheight=100

# Visualize layer overlaps
./gradlew visualizeOverlaps -PpsdFile=input.psd

# Create before/after comparison
./gradlew generateComparison -PinputFile=in.psd -PoutputFile=out.psd
```

**Key files to modify:**
- Core trapping logic: `src/main/java/org/electrosaur/trapper/PsdColorSeparator.java:applyTrapping()`
- Trap calculation: `src/main/java/org/electrosaur/trapper/AbstractTrappingStrategy.java:calculateTrapSize()`
- GUI interface: `src/main/java/org/electrosaur/trapper/TrapperGUI.java`
- Integration tests: `src/test/java/org/electrosaur/trapper/TrappingIntegrationTest.java`
