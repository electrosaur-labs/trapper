# Trapper Roadmap - Dual-Purpose Color Trapping Tool

**Document Date**: January 1, 2026
**Version**: 1.0
**Purpose**: Strategic roadmap for building a full-featured dual-purpose color trapping tool supporting both offset lithography and screen printing

---

## 🎯 Vision

Build the **first open-source, dual-purpose color trapping tool** that serves both:
1. **Offset Lithography**: Commercial printing, packaging, publications
2. **Screen Printing**: Garment printing, posters, textiles, signs

**Core Principle**: Maintain separate, correct trapping strategies for each printing method while sharing common infrastructure.

---

## 📊 Current State Assessment

### ✅ Strengths (Already Implemented)
- Automated color trapping with morphological dilation
- Configurable trap sizes (fractional inches and decimals)
- PackBits RLE compression (96.6% file size reduction)
- Parallel processing (3.2x speedup on 16 cores)
- White layer optimization (skips unnecessary computation)
- Built-in verification (flattened output matches original)
- PSD format support (native multi-layer)
- Comprehensive test suite (25 tests, 100% pass rate)
- Production-ready code quality
- Cross-platform (Java 21)

### ✅ Works Perfectly For
- **Offset Lithography**: Light spreads into dark (current implementation)
- High-precision commercial printing (0.003" to 1/32" traps)
- 3000 DPI film output workflows

### ❌ Critical Issues for Screen Printing
1. **Wrong trap direction**: Light→Dark (should be Dark→Light)
2. **Missing underbase generation**: Essential for dark garments
3. **Missing underbase choke**: White layer must be 2-4 points smaller
4. **Wrong units**: Only fractional inches (needs point-based: "2pt", "4pt")
5. **Wrong trap sizes**: Too small (0-1/32" vs. needed 2-6 points)
6. **Missing film positives**: No 1-bit TIFF output per layer
7. **Missing halftones**: No 45-65 LPI screen support
8. **Missing registration marks**: Essential for multi-color alignment

---

## 🎨 Architecture Vision: Dual-Mode Design

### Command-Line Interface

```bash
# Offset lithography mode (current behavior)
./gradlew runColorSeparator -PpsdFile=input.psd -Pmode=offset -PminTrap=0 -PmaxTrap=1/32

# Screen printing mode (new behavior)
./gradlew runColorSeparator -PpsdFile=input.psd -Pmode=screen -PminTrap=0pt -PmaxTrap=4pt

# With underbase for dark garments
./gradlew runColorSeparator -PpsdFile=input.psd -Pmode=screen -PminTrap=0pt -PmaxTrap=4pt \
  -Punderbase=true -PunderbaseChoke=3pt -PgarmentColor=black

# Film positive output
./gradlew runColorSeparator -PpsdFile=input.psd -Pmode=screen -PfilmOutput=true \
  -PhalftoneFrequency=55 -PregistrationMarks=true
```

### Core Architecture Changes

```
PsdColorSeparator (main entry point)
├── TrappingStrategy interface
│   ├── OffsetTrappingStrategy (light spreads into dark)
│   └── ScreenPrintingTrappingStrategy (dark traps over light)
├── UnderbaseGenerator (screen printing only)
│   ├── generateUnderbase() - creates white layer
│   └── chokeUnderbase() - morphological erosion
├── TrapSizeParser (enhanced)
│   ├── parseFractionalInches() - existing: "1/32", "1/64"
│   └── parsePoints() - new: "2pt", "4pt", "6pt"
├── FilmPositiveGenerator
│   ├── generate1BitTIFF() - black ink on white
│   └── addRegistrationMarks() - crosshairs, alignment
├── HalftoneGenerator
│   ├── generateHalftone() - 45-65 LPI for screen printing
│   └── calculateMeshCount() - 4× LPI minimum
└── OutputGenerator
    ├── writePSD() - existing multi-layer output
    └── writeFilmPositives() - separate TIFF per layer
```

---

## 🗓️ Implementation Roadmap

### **Phase 1: Foundation (Weeks 1-3)**

#### 1.1: Core Architecture Refactoring (Week 1)
**Goal**: Introduce dual-mode architecture without breaking existing functionality

**Tasks**:
- [ ] Create `TrappingStrategy` interface
  ```java
  public interface TrappingStrategy {
      int calculateExpansion(int layerIndex, int totalLayers, int dpi,
                            double minTrap, double maxTrap);
      String getName();
      String getDescription();
  }
  ```

- [ ] Implement `OffsetTrappingStrategy`
  - Move existing `calculateExpansion()` logic here
  - Lightest layer gets maximum trap
  - Linear interpolation light→dark

- [ ] Implement `ScreenPrintingTrappingStrategy`
  - **REVERSE LOGIC**: Darkest layer gets maximum trap
  - Linear interpolation dark→light
  - Formula: `ratio = (totalLayers - 1 - layerIndex) / (totalLayers - 1)`

- [ ] Add `--mode` parameter
  - `offset` (default, maintains backward compatibility)
  - `screen` (new screen printing mode)

- [ ] Add integration tests
  - Test offset mode produces same results as before
  - Test screen mode reverses trap direction correctly

**Success Criteria**:
- ✅ All existing tests pass
- ✅ Offset mode produces identical output to current version
- ✅ Screen mode reverses trap direction (verified manually)
- ✅ No breaking changes to existing API

---

#### 1.2: Point-Based Measurements (Week 2)
**Goal**: Support screen printing standard units (points)

**Tasks**:
- [ ] Enhance `parseTrapSize()` method
  ```java
  private static double parseTrapSize(String sizeSpec) {
      if (sizeSpec.endsWith("pt")) {
          // Parse "2pt", "4pt", "6pt"
          double points = Double.parseDouble(sizeSpec.substring(0, sizeSpec.length() - 2));
          return points / 72.0; // Convert points to inches
      }
      // Existing fractional/decimal logic
      // ...
  }
  ```

- [ ] Update command-line argument parsing
  - Accept: `0pt`, `2pt`, `4pt`, `6pt`
  - Accept: `0`, `0.028`, `0.056` (inches)
  - Accept: `0`, `1/32`, `1/64` (fractions)

- [ ] Update help text and documentation
  ```
  Trap sizes can be specified as:
    - Points: 2pt, 4pt, 6pt (screen printing standard)
    - Fractions: 1/32, 1/64, 1/16 (offset lithography)
    - Decimals: 0.03125, 0.015625 (inches)
  ```

- [ ] Add unit tests
  - Test parsing "2pt" → 0.02778 inches
  - Test parsing "4pt" → 0.05556 inches
  - Test parsing "6pt" → 0.08333 inches
  - Test error handling for invalid formats

**Success Criteria**:
- ✅ Accepts all three unit formats (points, fractions, decimals)
- ✅ Correct conversion: 72 points = 1 inch
- ✅ Clear error messages for invalid input
- ✅ 100% test coverage for parsing logic

---

#### 1.3: Update Defaults for Screen Printing (Week 3)
**Goal**: Sensible defaults for both modes

**Tasks**:
- [ ] Mode-specific default trap sizes
  ```java
  if (mode.equals("offset")) {
      defaultMinTrap = 0.0;        // 0 inches
      defaultMaxTrap = 1.0/32.0;   // 1/32 inch
  } else if (mode.equals("screen")) {
      defaultMinTrap = 0.0;        // 0 points
      defaultMaxTrap = 4.0/72.0;   // 4 points in inches
  }
  ```

- [ ] Add DPI validation warnings
  ```java
  if (mode.equals("screen") && dpi > 1200) {
      System.err.println("WARNING: DPI > 1200 is unusually high for screen printing");
      System.err.println("         Typical range: 300-600 DPI");
      System.err.println("         Continue? (y/n)");
  }
  ```

- [ ] Update README with mode-specific guidance
  - Offset: 3000 DPI, 0 to 1/32"
  - Screen: 300-600 DPI, 0 to 4pt

**Success Criteria**:
- ✅ Sensible defaults for each mode
- ✅ Helpful warnings for unusual parameters
- ✅ Clear documentation of best practices

---

### **Phase 2: Screen Printing Essentials (Weeks 4-6)**

#### 2.1: Underbase Generation (Week 4)
**Goal**: Generate white underbase layer for dark garments

**Tasks**:
- [ ] Create `UnderbaseGenerator` class
  ```java
  public class UnderbaseGenerator {
      /**
       * Generates white underbase covering all non-white colors
       * @param flattenedImage - original design flattened
       * @param garmentColor - background color (e.g., black, navy)
       * @return BufferedImage - white layer coverage
       */
      public static BufferedImage generateUnderbase(
          BufferedImage flattenedImage,
          Color garmentColor) {
          // Create white layer for all non-background pixels
          // ...
      }
  }
  ```

- [ ] Add command-line parameters
  - `--underbase=true/false` (default: false)
  - `--garmentColor=<color>` (default: white)
    - Accepts: "white", "black", "navy", "red", etc.
    - Accepts: RGB hex codes: "#000000", "#1A1A1A"

- [ ] Generate underbase mask
  - Identify all pixels that need white ink underneath
  - Exclude pixels that match garment color
  - Handle transparency correctly

- [ ] Layer ordering
  - Underbase must be first layer in print sequence
  - Followed by colors from dark to light

- [ ] Integration with existing workflow
  - Insert underbase generation before color separation
  - Update layer count and indexing

**Success Criteria**:
- ✅ Underbase covers all design areas (no garment color showing)
- ✅ Correct layer ordering (underbase first)
- ✅ Works with various garment colors
- ✅ Integration tests with real designs

---

#### 2.2: Underbase Choke (Week 5)
**Goal**: Shrink underbase by 2-4 points to prevent white halo

**Tasks**:
- [ ] Implement morphological erosion
  ```java
  /**
   * Applies choke (erosion) to underbase layer
   * @param underbase - white layer image
   * @param chokeAmount - pixels to shrink (2-4 points at DPI)
   * @return BufferedImage - choked underbase
   */
  public static BufferedImage chokeUnderbase(
      BufferedImage underbase,
      int chokeAmount) {
      // Iterative erosion using 4-connected neighbors
      // Opposite of dilation used for trapping
      // ...
  }
  ```

- [ ] Add `--underbaseChoke` parameter
  - Default: 3pt (0.04167 inches)
  - Range: 2pt to 6pt (0.028" to 0.083")
  - Accepts same formats as trap sizes (points, fractions, decimals)

- [ ] Implement erosion algorithm
  - Iterative shrinking (opposite of dilation)
  - 4-connected neighbors (consistent with existing code)
  - Progress reporting for large images

- [ ] Verification
  - Ensure choked underbase is smaller than original
  - Verify no "islands" or disconnected regions
  - Visual comparison tools

**Success Criteria**:
- ✅ Underbase properly choked (2-4 points smaller)
- ✅ No white halo visible on dark garments
- ✅ Consistent with screen printing industry practice
- ✅ Performance acceptable for large images

---

#### 2.3: Screen Printing Validation (Week 6)
**Goal**: Ensure screen printing output follows industry standards

**Tasks**:
- [ ] Add validation checks
  - Trap sizes appropriate for screen printing (2-6 points)
  - DPI reasonable for screen printing (300-600)
  - Layer count manageable (typically 4-8 colors for garments)
  - Underbase present when garment is dark

- [ ] Generate separation report
  ```
  Screen Printing Separation Report
  ==================================
  Mode: Screen Printing
  Input: design.psd (1200x1800 pixels)
  DPI: 600
  Colors detected: 6
  Trap range: 0pt to 4pt

  Layer 1: White Underbase (choked 3pt)
  Layer 2: Yellow (trap: 4.0pt / 0.056")
  Layer 3: Orange (trap: 3.2pt / 0.044")
  Layer 4: Red (trap: 2.4pt / 0.033")
  Layer 5: Navy (trap: 1.6pt / 0.022")
  Layer 6: Black (trap: 0.8pt / 0.011")
  Layer 7: Dark Gray (trap: 0pt - defines edges)

  Mesh recommendations:
  - 110 mesh (27 LPI): Underbase
  - 156 mesh (39 LPI): Yellow, Orange
  - 196 mesh (49 LPI): Red, Navy, Black, Gray

  Verification: PASSED
  Total file size: 45 MB (compressed)
  ```

- [ ] Add warnings for common issues
  - "Too many colors (>10) - consider reducing for screen printing"
  - "Very small details (<0.5pt) may not print well on fabric"
  - "High DPI (>1200) unnecessary for screen printing"

**Success Criteria**:
- ✅ Validation prevents common screen printing errors
- ✅ Helpful separation report generated
- ✅ Warnings guide users to best practices

---

### **Phase 3: Professional Features (Weeks 7-10)**

#### 3.1: Film Positive Output (Week 7)
**Goal**: Generate 1-bit TIFF files suitable for screen burning

**Tasks**:
- [ ] Create `FilmPositiveGenerator` class
  ```java
  public class FilmPositiveGenerator {
      /**
       * Generates 1-bit TIFF film positive for a layer
       * @param layer - color layer image (grayscale or color)
       * @param layerName - name for output file
       * @param outputDir - directory for film positives
       * @return File - path to generated TIFF
       */
      public static File generateFilmPositive(
          BufferedImage layer,
          String layerName,
          File outputDir) {
          // Convert to 1-bit (black ink on white background)
          // Apply threshold (128 default)
          // Write TIFF with correct metadata
          // ...
      }
  }
  ```

- [ ] Add `--filmOutput` parameter
  - `--filmOutput=true` generates separate TIFF files
  - `--filmOutputDir=./films` (default subdirectory)
  - Files named: `design_Layer1_White.tif`, `design_Layer2_Yellow.tif`, etc.

- [ ] 1-bit conversion
  - Threshold: pixels > 128 → black ink, else white background
  - Configurable threshold: `--filmThreshold=128` (0-255)
  - Dithering option: `--filmDither=true` (Floyd-Steinberg)

- [ ] TIFF metadata
  - Resolution: Match source DPI
  - Compression: LZW or PackBits
  - Photometric interpretation: MinIsWhite (0=white, 1=black)

- [ ] Batch generation
  - Generate all layers in parallel
  - Progress reporting
  - Error handling (disk space, permissions)

**Success Criteria**:
- ✅ Film positives open correctly in image viewers
- ✅ 1-bit TIFFs with proper metadata
- ✅ Suitable for direct printing to transparency film
- ✅ File naming clear and consistent

---

#### 3.2: Registration Marks (Week 8)
**Goal**: Add crosshairs and alignment marks to film positives

**Tasks**:
- [ ] Create `RegistrationMarkGenerator` class
  ```java
  public class RegistrationMarkGenerator {
      /**
       * Adds registration marks to film positive
       * @param image - film positive image
       * @param markType - CROSSHAIR, CIRCLE, or BOTH
       * @param position - CORNERS or MIDPOINTS
       * @return BufferedImage - image with registration marks
       */
      public static BufferedImage addRegistrationMarks(
          BufferedImage image,
          MarkType markType,
          MarkPosition position) {
          // Draw registration crosshairs at specified positions
          // Typically at 4 corners or midpoints of edges
          // ...
      }
  }
  ```

- [ ] Registration mark types
  - **Crosshair**: ⊕ (circle with cross)
  - **Target**: Concentric circles
  - **Crop marks**: Corner L-shapes
  - **Configurable**: `--regMarks=crosshair,cropmarks`

- [ ] Mark positioning
  - Outside image area (bleed zone)
  - Consistent across all layers
  - Configurable margin: `--regMargin=0.25` (inches)

- [ ] Mark styling
  - Line thickness: 0.5pt (hairline)
  - Size: 0.125" to 0.25" diameter
  - Black on white background

- [ ] Add to film positives
  - All layers get identical registration marks
  - Printed on same transparency as color
  - Used for manual or automatic alignment

**Success Criteria**:
- ✅ Registration marks consistent across all layers
- ✅ Marks visible but not intrusive
- ✅ Industry-standard styling
- ✅ Helpful for multi-color alignment

---

#### 3.3: Halftone Generation (Weeks 9-10)
**Goal**: Generate halftone screens for screen printing (45-65 LPI)

**Tasks**:
- [ ] Create `HalftoneGenerator` class
  ```java
  public class HalftoneGenerator {
      /**
       * Converts continuous tone image to halftone
       * @param image - grayscale or color image
       * @param frequency - lines per inch (45-65 typical)
       * @param angle - screen angle in degrees
       * @param spotFunction - DOT, LINE, ELLIPSE, or SQUARE
       * @return BufferedImage - halftoned image
       */
      public static BufferedImage generateHalftone(
          BufferedImage image,
          int frequency,
          double angle,
          SpotFunction spotFunction) {
          // Ordered dithering or error diffusion
          // Create halftone pattern based on spot function
          // ...
      }
  }
  ```

- [ ] Halftone parameters
  - `--halftone=true/false` (enable halftone generation)
  - `--halftoneFrequency=55` (LPI, default 55 for screen printing)
  - `--halftoneAngle=45` (degrees, default 45°)
  - `--halftoneSpot=dot` (dot, line, ellipse, square)

- [ ] Screen angles
  - Standard CMYK angles:
    - Cyan: 15°
    - Magenta: 75°
    - Yellow: 0°
    - Black: 45°
  - Spot colors: User-configurable
  - Auto-assign angles to avoid moiré

- [ ] Spot functions
  - **Dot**: Round halftone dots (most common)
  - **Line**: Linear screen (for directional effects)
  - **Ellipse**: Elliptical dots
  - **Square**: Square halftone cells

- [ ] Mesh count recommendations
  ```java
  /**
   * Calculates minimum mesh count for halftone frequency
   * Rule of thumb: Mesh = 4× LPI minimum
   */
  public static int recommendMeshCount(int lpi) {
      return lpi * 4; // e.g., 55 LPI → 220 mesh minimum
  }
  ```

- [ ] Integration
  - Apply halftone before film positive generation
  - Option to halftone only specific layers
  - Preview mode to test halftone appearance

**Success Criteria**:
- ✅ Halftones suitable for screen printing (45-65 LPI)
- ✅ Correct screen angles to avoid moiré
- ✅ Mesh count recommendations accurate
- ✅ Visual quality acceptable for garment printing

---

### **Phase 4: Advanced Features (Weeks 11-14)**

#### 4.1: Spot Color Detection (Week 11)
**Goal**: Automatically identify and separate spot colors (Pantone, etc.)

**Tasks**:
- [ ] Create `SpotColorDetector` class
  - Analyze color distribution in design
  - Identify pure spot colors (no halftone mixing)
  - Distinguish from process colors (CMYK)

- [ ] Spot color separation
  - Keep spot colors as separate layers
  - Don't attempt to mix spot colors
  - Preserve spot color information in output

- [ ] Pantone matching
  - Optional: Match RGB to closest Pantone color
  - Library of common Pantone colors
  - User override for manual Pantone assignment

- [ ] Benefits for screen printing
  - Each spot color = one screen
  - More vibrant than process color
  - Easier color matching on press

**Success Criteria**:
- ✅ Correctly identifies spot colors
- ✅ Separates spot colors to individual layers
- ✅ Optional Pantone matching

---

#### 4.2: Simulated Process Color (Week 12)
**Goal**: Convert full-color images to limited spot colors for screen printing

**Tasks**:
- [ ] Implement CMYK separation
  - Convert RGB to CMYK
  - Separate to Cyan, Magenta, Yellow, Black layers
  - Optional: Add white underbase

- [ ] Index color separation
  - Reduce to N colors (typically 4-8 for garments)
  - Color quantization algorithms (median cut, k-means)
  - Preserve most important colors

- [ ] Simulated process
  - Halftone each color layer
  - Overprint simulation (show final appearance)
  - Order colors by lightness (light to dark)

**Success Criteria**:
- ✅ CMYK separation produces 4-5 screens (CMYK + underbase)
- ✅ Index separation reduces colors intelligently
- ✅ Preview shows expected final appearance

---

#### 4.3: Batch Processing & Workflow Automation (Week 13)
**Goal**: Process multiple files with consistent settings

**Tasks**:
- [ ] Batch mode
  ```bash
  ./gradlew runColorSeparator -Pbatch=true -PinputDir=./designs -PoutputDir=./output \
    -Pmode=screen -PminTrap=0pt -PmaxTrap=4pt -Punderbase=true
  ```

- [ ] Configuration files
  - JSON or YAML configuration
  - Save common settings as presets
  - Example: "garment_dark.json", "poster_4color.json"

- [ ] Job queue
  - Process multiple files sequentially
  - Parallel processing of independent jobs
  - Progress reporting and logging

- [ ] Error handling
  - Continue on error (don't stop entire batch)
  - Detailed error log with file names
  - Success/failure summary report

**Success Criteria**:
- ✅ Batch processing of multiple files
- ✅ Configuration presets for common workflows
- ✅ Robust error handling and logging

---

#### 4.4: GUI Interface (Optional - Week 14)
**Goal**: Graphical interface for non-technical users

**Tasks**:
- [ ] JavaFX or Swing GUI
  - File selection (drag and drop)
  - Mode selection (offset vs. screen)
  - Parameter inputs (trap sizes, underbase, etc.)
  - Preview pane (show layers and trapping)

- [ ] Visual feedback
  - Layer preview with zoom/pan
  - Before/after comparison
  - Trap overlay visualization

- [ ] One-click presets
  - "Dark Garment (4-color)"
  - "Light Garment (no underbase)"
  - "Poster (spot colors)"
  - "Offset Lithography (high precision)"

**Success Criteria**:
- ✅ Intuitive GUI for beginners
- ✅ Doesn't compromise CLI functionality
- ✅ Cross-platform (Windows, Mac, Linux)

---

### **Phase 5: Polish & Release (Weeks 15-16)**

#### 5.1: Documentation (Week 15)
**Goal**: Comprehensive documentation for both modes

**Tasks**:
- [ ] Update README.md
  - Dual-mode explanation (offset vs. screen)
  - Quick start guides for each mode
  - Feature comparison table
  - FAQ section

- [ ] Create user guides
  - **Screen Printing Guide**: Garment printing workflow
  - **Offset Lithography Guide**: Commercial printing workflow
  - Troubleshooting common issues
  - Best practices for each mode

- [ ] API documentation
  - JavaDoc for all public classes/methods
  - Architecture overview
  - Extension guide (how to add new features)

- [ ] Video tutorials
  - "Getting Started with Screen Printing"
  - "Color Trapping for Offset Lithography"
  - "Understanding Trap Direction"
  - "Generating Film Positives"

**Success Criteria**:
- ✅ Complete, clear documentation
- ✅ Users can get started without external help
- ✅ Both beginners and experts can find needed info

---

#### 5.2: Testing & Quality Assurance (Week 16)
**Goal**: Ensure production-ready quality for both modes

**Tasks**:
- [ ] Expand test suite
  - Unit tests for all new classes
  - Integration tests for both modes
  - Performance benchmarks
  - Real-world design testing

- [ ] Test coverage goals
  - >90% code coverage
  - 100% coverage for critical paths (trapping logic)
  - Edge case testing (1 color, 10+ colors, etc.)

- [ ] Beta testing
  - Screen printers test screen printing mode
  - Offset printers test offset mode
  - Collect feedback and iterate

- [ ] Performance optimization
  - Profile hot paths
  - Optimize slow operations
  - Memory usage analysis

**Success Criteria**:
- ✅ >90% test coverage
- ✅ Zero known critical bugs
- ✅ Performance acceptable for production use
- ✅ Positive feedback from beta testers

---

#### 5.3: Release Preparation (Week 16)
**Goal**: Publish v2.0 with dual-mode support

**Tasks**:
- [ ] Version numbering
  - v2.0.0 (major release with breaking changes)
  - Semantic versioning going forward

- [ ] Release artifacts
  - JAR file with all dependencies
  - Source code ZIP
  - Documentation package
  - Example files and presets

- [ ] Release notes
  - Changelog (all new features)
  - Migration guide (v1.x → v2.0)
  - Known issues and limitations

- [ ] GitHub release
  - Tag v2.0.0
  - Upload release artifacts
  - Publish release notes

- [ ] Announcement strategy
  - Blog post: "Trapper v2.0 - Now with Screen Printing Support"
  - Reddit: r/screenprinting, r/printing
  - Forums: PrintPlanet, T-ShirtForums
  - Social media posts

**Success Criteria**:
- ✅ Clean, professional release
- ✅ All artifacts available for download
- ✅ Announcement reaches target audiences

---

## 📏 Success Metrics

### Technical Metrics
- **Test Coverage**: >90% code coverage
- **Performance**: <5 minutes for 5000×4000 image at 600 DPI
- **File Size**: >90% compression ratio with RLE
- **Quality**: Zero pixel differences in verification

### User Metrics
- **Downloads**: Track GitHub releases
- **Stars**: GitHub stars as popularity indicator
- **Issues**: Monitor bug reports and feature requests
- **Contributions**: Attract open source contributors

### Market Metrics
- **Offset Lithography**: First open source trapping tool
- **Screen Printing**: Compete with $860-1,360/year commercial solutions
- **User Base**: Target 1,000+ users in first year
- **Community**: Active forum discussions, tutorials, examples

---

## 🎯 Target Markets

### Primary: Screen Printing
1. **Home-Based Garment Printers**
   - Pain point: Can't afford $1,000+/year for T-Seps + Photoshop
   - Value: Free, professional separations
   - Market size: Growing (thousands worldwide)

2. **Small Screen Printing Shops**
   - Pain point: Need reliable, scriptable workflow
   - Value: Batch processing, no subscription costs
   - Market size: 1-5 employee shops (thousands globally)

3. **Screen Printing Schools**
   - Pain point: Need affordable tools for students
   - Value: Free, educational, open source
   - Market size: Trade schools, community colleges

4. **Developing Markets**
   - Pain point: Limited access to commercial software
   - Value: No licensing barriers, free distribution
   - Market size: Asia, Africa, Latin America (huge potential)

### Secondary: Offset Lithography
1. **Small Print Shops**
   - Pain point: Can't afford Kodak Prinergy ($$$$)
   - Value: Free, production-ready trapping
   - Market size: Small commercial printers globally

2. **Design Studios**
   - Pain point: Need print-ready file preparation
   - Value: Standalone tool, no expensive prepress software
   - Market size: Thousands of design studios

3. **Packaging Companies**
   - Pain point: Need customizable trapping
   - Value: Open source, modifiable
   - Market size: Small packaging printers

---

## 💰 Competitive Analysis

### Screen Printing
| Solution | Cost (Year 1) | Annual Cost | Standalone | Open Source | Trapping | Underbase |
|----------|--------------|-------------|------------|-------------|----------|-----------|
| **T-Seps** | $1,160 | $660 | ❌ (Photoshop) | ❌ | ✅ | ✅ |
| **Separation Studio** | $1,360 | $660 | ❌ (Photoshop) | ❌ | ✅ | ✅ |
| **UltraSeps** | $960 | $660 | ❌ (Photoshop) | ❌ | ⚠️ | ⚠️ |
| **Magic Buttons** | $860 | $660 | ❌ (Photoshop) | ❌ | ⚠️ | ⚠️ |
| **Trapper v2.0** | **$0** | **$0** | ✅ | ✅ | ✅ | ✅ |

**Advantage**: Save $860-1,360 first year, $660/year ongoing

### Offset Lithography
| Solution | Cost | Standalone | Open Source | Trapping | Compression | Parallel |
|----------|------|------------|-------------|----------|-------------|----------|
| **Kodak Prinergy** | $$$$ | ✅ | ❌ | ✅ | ✅ | ✅ |
| **Adobe In-RIP** | $$$ | ⚠️ | ❌ | ✅ | ✅ | ⚠️ |
| **Esko ArtPro+** | $$$$ | ✅ | ❌ | ✅ | ✅ | ✅ |
| **Scribus** | Free | ✅ | ✅ | ❌ | ⚠️ | ❌ |
| **Trapper v2.0** | **Free** | ✅ | ✅ | ✅ | ✅ | ✅ |

**Advantage**: Only open source solution with automated trapping

---

## 🚀 Go-to-Market Strategy

### Phase 1: Soft Launch (Weeks 15-16)
- GitHub release v2.0.0
- Announcement on niche forums
- Reach out to beta testers for testimonials
- Publish blog post on project website

### Phase 2: Community Engagement (Months 2-3)
- Reddit posts in relevant subreddits
- Screen printing forum announcements
- YouTube tutorial videos
- Answer questions, provide support

### Phase 3: Broader Awareness (Months 4-6)
- Submit to Hacker News, Product Hunt
- Reach out to printing industry publications
- Conference presentations (if applicable)
- Build case studies with real users

### Phase 4: Ecosystem Growth (Months 6-12)
- Encourage community contributions
- Create plugin architecture for extensions
- Partner with related open source projects
- Consider commercial support options (consulting, training)

---

## 🔮 Future Enhancements (Beyond v2.0)

### Advanced Color Management
- ICC profile support
- Color calibration tools
- Pantone color library integration
- Lab/HSL color space support

### Web-Based Version
- Browser-based tool (WebAssembly)
- No installation required
- Cloud processing option
- Collaboration features

### AI-Assisted Features
- Automatic color palette reduction
- Intelligent underbase generation (detect garment color from image)
- Trap size recommendation based on image analysis
- Quality prediction (estimate print success rate)

### Integration & Plugins
- Photoshop plugin (ironically!)
- GIMP plugin
- Inkscape extension
- REST API for web services

### Print Shop Management
- Job tracking and costing
- Ink consumption estimates
- Production scheduling
- Customer proofing system

---

## 📊 Risk Assessment

### Technical Risks
| Risk | Probability | Impact | Mitigation |
|------|------------|--------|------------|
| Performance degradation with new features | Medium | High | Profile and optimize, maintain benchmarks |
| Compatibility issues across platforms | Low | Medium | Test on Windows, Mac, Linux regularly |
| Complex codebase becomes unmaintainable | Low | High | Maintain clean architecture, document thoroughly |

### Market Risks
| Risk | Probability | Impact | Mitigation |
|------|------------|--------|------------|
| Limited adoption due to niche market | Medium | High | Focus on screen printing (larger market) |
| Commercial competitors add open source | Low | Medium | First-mover advantage, community building |
| Technical support burden overwhelming | Medium | Medium | Good documentation, community forum |

### Operational Risks
| Risk | Probability | Impact | Mitigation |
|------|------------|--------|------------|
| Single maintainer (bus factor) | High | High | Document everything, attract contributors |
| Scope creep delays release | Medium | Medium | Strict roadmap adherence, MVP approach |
| Legal issues (patent claims) | Low | High | Prior art research, defensive publication |

---

## 🎓 Learning Resources

### For Contributors
- **Color Theory**: Understanding RGB, CMYK, Lab color spaces
- **Print Technologies**: Offset lithography vs. screen printing fundamentals
- **Image Processing**: Morphological operations, halftoning, dithering
- **Java Development**: Modern Java (21+), parallel processing, testing

### Recommended Reading
1. "The Color Printing Handbook" - industry standard reference
2. "Digital Prepress for Comic Books" - practical color separation
3. "Screen Printing: The Complete Water-Based System" - screen printing techniques
4. Adobe Photoshop File Format Specification (PSD)
5. TIFF 6.0 Specification (film output format)

---

## 📝 Conclusion

This roadmap outlines a **16-week path** to building a full-featured, dual-purpose color trapping tool that serves both offset lithography and screen printing markets.

### Key Success Factors:
1. ✅ **Maintain backward compatibility** - don't break existing offset workflows
2. ✅ **Screen printing first** - focus on highest-impact features (underbase, trap reversal)
3. ✅ **Quality over quantity** - polish each feature before moving on
4. ✅ **Community driven** - engage users early and often
5. ✅ **Open source ethos** - transparent, collaborative, welcoming

### The Opportunity:
- **Offset lithography**: Already production-ready, fills genuine open source gap
- **Screen printing**: After enhancements, saves users $860-1,360/year vs. commercial alternatives
- **Market potential**: Thousands of small shops and home printers worldwide
- **Competitive moat**: First and only open source solution in both markets

**This is a genuine opportunity to create a valuable, widely-adopted open source tool that serves real user needs.** 🎉

---

**Document Version**: 1.0
**Date**: January 1, 2026
**Author**: Roadmap for Trapper dual-purpose color trapping tool
**License**: This roadmap document is provided for planning purposes.
