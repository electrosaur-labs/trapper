# Screen Printing Enhancements for Trapper

**Document Date**: December 31, 2025
**Purpose**: Comprehensive analysis of required enhancements for screen printing applications
**Current State**: Tool optimized for offset lithography
**Target Use**: Screen printing on garments (t-shirts, apparel)

---

## 📋 Executive Summary

The current Trapper implementation is designed for **offset lithography** printing, which has fundamentally different requirements than **screen printing**. This document outlines 39 specific enhancements needed to make Trapper an effective screen printing tool.

### Critical Differences: Offset vs. Screen Printing

| Aspect | Offset Lithography (Current) | Screen Printing (Needed) |
|--------|------------------------------|--------------------------|
| **Registration Tolerance** | 0.003" precision | 2-6 points typical (0.028"-0.083") |
| **Trap Direction** | Lighter spreads into darker | Darker traps over lighter |
| **Underbase** | Not used | **Essential for dark garments** |
| **Trap Size** | 1/32" - 1/64" (0.03125"-0.015625") | 2-6 points (0.028"-0.083") |
| **Color Order** | Light to dark | White base → colors → black |
| **Halftones** | 150-300 LPI | 45-65 LPI |
| **Mesh Count** | N/A | 110-230 mesh |
| **Primary Medium** | Paper | Fabric (cotton, polyester, blends) |
| **DPI Standards** | 3000 DPI | 300-600 DPI |
| **Measurement Units** | Fractional inches (1/32") | Points (72 pts = 1") |

---

## 🎯 Priority 1: Essential Screen Printing Features

### 1. **Underbase Generation** ⭐⭐⭐⭐⭐

**Current State**: No underbase support
**Need**: Automatic white underbase layer for dark garments
**Priority**: CRITICAL - Cannot print on dark garments without this

**Description**:
An underbase is a layer of white ink printed first when printing on dark-colored garments. It prevents the garment color from showing through and allows bright colors to appear vibrant.

**Requirements**:
- Generate solid white layer under all colored areas
- Exclude pure white areas (no underbase needed there)
- Choke (shrink) edges by 2-4 points to prevent white show-through
- Must be first layer in print sequence

**Implementation Approach**:
```java
/**
 * Generates white underbase for printing on dark garments
 * @param source Original image
 * @param chokeAmount Amount to choke edges (2-4 points typical)
 * @param darkGarment Whether printing on dark garment
 * @return White underbase layer, choked appropriately
 */
private static BufferedImage generateUnderbase(
    BufferedImage source,
    int chokeAmount,  // 2-4 points typical
    boolean darkGarment
) {
    if (!darkGarment) {
        return null; // No underbase needed for light garments
    }

    // 1. Create white layer covering all non-white colors
    BufferedImage underbase = new BufferedImage(
        source.getWidth(),
        source.getHeight(),
        BufferedImage.TYPE_INT_ARGB
    );

    // 2. Fill with white where colors exist
    for (int y = 0; y < source.getHeight(); y++) {
        for (int x = 0; x < source.getWidth(); x++) {
            int pixel = source.getRGB(x, y);
            int alpha = (pixel >> 24) & 0xFF;
            int rgb = pixel & 0x00FFFFFF;

            // If not transparent and not pure white, add to underbase
            if (alpha > 0 && rgb != 0xFFFFFF) {
                underbase.setRGB(x, y, 0xFFFFFFFF); // Opaque white
            } else {
                underbase.setRGB(x, y, 0x00000000); // Transparent
            }
        }
    }

    // 3. Choke edges by specified amount
    BufferedImage choked = chokeUnderbase(underbase, chokeAmount);

    return choked;
}

/**
 * Chokes (erodes) the underbase to prevent show-through
 * White ink spreads more than colored inks, so underbase needs to be smaller
 */
private static BufferedImage chokeUnderbase(
    BufferedImage underbase,
    int chokeAmount  // in pixels, converted from points
) {
    // Use morphological erosion (opposite of dilation)
    // Remove pixels at edges iteratively
    BufferedImage result = underbase;

    for (int iteration = 0; iteration < chokeAmount; iteration++) {
        result = erodeByOnePixel(result);
    }

    return result;
}
```

**Benefits**:
- Essential for dark garment printing
- Prevents color show-through
- Industry standard requirement
- Enables vibrant colors on any garment color

**Related Settings**:
- Choke amount: 2-4 points (user configurable)
- Mesh count: 110-160 (lower for thick ink deposit)
- Print order: Always first layer

---

### 2. **Reverse Trap Direction** ⭐⭐⭐⭐⭐

**Current State**: Lighter colors spread into darker (offset method)
**Need**: Darker colors trap over lighter (screen printing method)
**Priority**: CRITICAL - Current direction is backwards for screen printing

**Description**:
In offset lithography, lighter colors spread under darker colors because darker inks are printed first. In screen printing, the opposite is true: darker, more opaque inks print last and should overlap lighter colors.

**Why Different**:
- Screen printing: darker inks are more opaque
- Want dark to overlap light (not light under dark)
- Black should trap over all colors
- Registration issues are more visible with light-over-dark

**Current Algorithm** (Offset):
```java
// Lightest layer (index 0): maxTrap (largest expansion)
// Darkest layer (index n-1): minTrap (smallest expansion)
double ratio = (double) layerIndex / (totalLayers - 1);
double expansionInches = maxExpansion - (ratio * (maxExpansion - minExpansion));
```

**Needed Algorithm** (Screen Printing):
```java
// Option 1: Only darkest traps (simplest)
int screenPrintTrap = (layerIndex == totalLayers - 1) ? maxTrap : 0;

// Option 2: Linear from dark to light (more sophisticated)
int reversedIndex = totalLayers - 1 - layerIndex;
double ratio = (double) reversedIndex / (totalLayers - 1);
double expansionInches = maxExpansion - (ratio * (maxExpansion - minExpansion));

// Result:
// - Darkest layer: maxTrap
// - Lightest layer: minTrap (usually 0)
// - Middle layers: Interpolated (darker gets more)
```

**Implementation**:
```java
/**
 * Calculate trap size based on printing method
 */
private static int calculateExpansion(
    int layerIndex,
    int totalLayers,
    int dpi,
    double minExpansion,
    double maxExpansion,
    PrintingMethod method  // OFFSET or SCREEN_PRINT
) {
    if (totalLayers == 1) {
        return (int) Math.round(minExpansion * dpi);
    }

    double ratio;

    if (method == PrintingMethod.SCREEN_PRINT) {
        // Reverse order: darker gets more trap
        int reversedIndex = totalLayers - 1 - layerIndex;
        ratio = (double) reversedIndex / (totalLayers - 1);
    } else {
        // Offset: lighter gets more trap
        ratio = (double) layerIndex / (totalLayers - 1);
    }

    double expansionInches = maxExpansion - (ratio * (maxExpansion - minExpansion));
    int expansionPixels = (int) Math.round(expansionInches * dpi);

    return expansionPixels;
}

enum PrintingMethod {
    OFFSET_LITHOGRAPHY,
    SCREEN_PRINT
}
```

**Impact**:
- **CRITICAL FIX** - current algorithm is backwards for screen printing
- Affects every separation
- Changes which colors overlap which
- Essential for proper registration

---

### 3. **Screen Printing Trap Sizes** ⭐⭐⭐⭐⭐

**Current State**: 0 to 1/32" (0.03125") - too small for screen printing
**Need**: 2-6 points (0.028" to 0.083")
**Priority**: CRITICAL - Current defaults don't work

**Typical Values**:
- **Tight registration**: 2 points (0.028")
- **Standard**: 3-4 points (0.042" - 0.056")
- **Loose registration**: 5-6 points (0.069" - 0.083")
- **Very loose**: 8 points (0.111") for difficult presses

**Why Larger**:
- Screen printing has looser registration than offset
- Manual or semi-automatic presses have more variation
- Fabric stretches during printing
- Multiple print stations increase cumulative error

**Comparison**:
```
Offset lithography:  0.003" = 0.216 points (very tight)
Current default:     1/32" = 2.25 points (minimum for screen)
Screen print tight:  2 points = 0.028"
Screen print std:    4 points = 0.056"
Screen print loose:  6 points = 0.083"
```

**Implementation**:
```java
// Add screen printing presets
private static final double SCREEN_PRINT_TIGHT = 2.0 / 72.0;      // 2 points
private static final double SCREEN_PRINT_STANDARD = 4.0 / 72.0;   // 4 points
private static final double SCREEN_PRINT_LOOSE = 6.0 / 72.0;      // 6 points

// Update default for screen printing
private static final double DEFAULT_MIN_EXPANSION_SCREEN = 0.0;
private static final double DEFAULT_MAX_EXPANSION_SCREEN = 4.0 / 72.0;  // 4 points

// Update parser to accept points
private static double parseTrapSize(String spec) {
    spec = spec.trim();

    // Check for points (e.g., "2pt", "4pt")
    if (spec.endsWith("pt")) {
        String numberPart = spec.substring(0, spec.length() - 2).trim();
        double points = Double.parseDouble(numberPart);
        return points / 72.0;  // Convert points to inches
    }

    // Check for fractional inches (existing code)
    if (spec.contains("/")) {
        String[] parts = spec.split("/");
        if (parts.length != 2) {
            throw new IllegalArgumentException("Invalid fraction format: " + spec);
        }
        double numerator = Double.parseDouble(parts[0].trim());
        double denominator = Double.parseDouble(parts[1].trim());
        if (denominator == 0) {
            throw new IllegalArgumentException("Division by zero");
        }
        return numerator / denominator;
    }

    // Decimal inches
    return Double.parseDouble(spec);
}
```

**Usage Examples**:
```bash
# Screen printing presets
./gradlew runColorSeparator -PpsdFile=design.psd -PminTrap=0 -PmaxTrap=2pt    # Tight
./gradlew runColorSeparator -PpsdFile=design.psd -PminTrap=0 -PmaxTrap=4pt    # Standard
./gradlew runColorSeparator -PpsdFile=design.psd -PminTrap=0 -PmaxTrap=6pt    # Loose

# Equivalent in inches
./gradlew runColorSeparator -PpsdFile=design.psd -PminTrap=0 -PmaxTrap=0.056  # 4 points
```

---

### 4. **Choke the Underbase** ⭐⭐⭐⭐⭐

**Current State**: No choke support
**Need**: Underbase should be 2-4 points smaller than top colors
**Priority**: CRITICAL for clean prints

**Description**:
White ink spreads more than colored inks during screen printing. If the underbase is the same size as the top colors, white will show around edges ("halo effect"). Choking the underbase by 2-4 points prevents this.

**Why Essential**:
- White ink is thicker and spreads more
- Prevents white "halo" around edges
- Allows top colors to slightly overlap underbase
- Industry standard practice

**Technical Details**:
- Choke amount: 2-4 points typical
- Use morphological erosion (opposite of dilation)
- Apply after underbase generation
- Different from trap (which expands)

**Implementation** (see #1 above - `chokeUnderbase()` method)

**Visual Example**:
```
Before choke:           After 2-point choke:
┌─────────────┐         ┌─────────────┐
│White        │         │   White     │
│Underbase    │  --->   │ Underbase   │ <- Smaller
│             │         │             │
└─────────────┘         └─────────────┘
     ▼                       ▼
┌─────────────┐         ┌─────────────┐
│Red Top Color│         │Red Top Color│
│             │         │   overlaps  │ <- Covers edge
└─────────────┘         └─────────────┘

Result: No white halo, clean edges
```

---

### 5. **Halftone Support** ⭐⭐⭐⭐

**Current State**: Solid colors only
**Need**: Generate halftone screens for gradients and photos
**Priority**: HIGH - Required for photographic prints

**Screen Printing Halftone Specs**:
- **LPI (Lines Per Inch)**: 45-65 LPI (much coarser than offset's 150-300 LPI)
- **Common**: 55 LPI for standard work
- **Maximum**: 65 LPI for fine detail with 230+ mesh
- **Minimum**: 45 LPI for coarser work with 110-160 mesh
- **Angles**: 22.5° increments to avoid moiré
- **Mesh Rule**: Mesh count should be 4x the LPI minimum

**Why Different from Offset**:
- Larger mesh openings require larger dots
- Fabric texture affects resolution
- Hand-feel considerations (fewer dots = softer)
- Ink deposit is thicker

**Mesh Count Relationship**:
```
LPI × 4 = Minimum Mesh Count
45 LPI × 4 = 180 mesh
55 LPI × 4 = 220 mesh
65 LPI × 4 = 260 mesh (rare, usually use 230)
```

**Implementation**:
```java
/**
 * Generates halftone screen for photographic images
 * @param source Grayscale or color channel
 * @param lpi Lines per inch (45-65 typical for screen printing)
 * @param angle Screen angle in degrees (22.5° increments)
 * @param meshCount Mesh count for documentation
 * @return Halftoned image (black dots on white)
 */
private static BufferedImage generateHalftone(
    BufferedImage source,
    int lpi,           // 45-65 typical
    double angle,      // screen angle in degrees
    int meshCount,     // for documentation/validation
    int dpi            // output resolution
) {
    // Validate mesh count
    int minMesh = lpi * 4;
    if (meshCount < minMesh) {
        System.out.printf(
            "Warning: %d mesh is too low for %d LPI. Minimum: %d mesh%n",
            meshCount, lpi, minMesh
        );
    }

    // Convert to grayscale if needed
    BufferedImage gray = convertToGrayscale(source);

    // Calculate dot spacing in pixels
    double dotSpacing = (double) dpi / lpi;

    // Generate halftone dots
    BufferedImage halftone = new BufferedImage(
        source.getWidth(),
        source.getHeight(),
        BufferedImage.TYPE_BYTE_BINARY  // 1-bit black & white
    );

    // For each halftone cell
    for (int y = 0; y < gray.getHeight(); y += dotSpacing) {
        for (int x = 0; x < gray.getWidth(); x += dotSpacing) {
            // Sample original image
            int grayValue = sampleArea(gray, x, y, (int)dotSpacing);

            // Calculate dot size (0-100% based on gray value)
            double dotSize = grayValue / 255.0;

            // Draw dot at rotated position
            drawHalftoneDot(halftone, x, y, dotSize, angle, dotSpacing);
        }
    }

    return halftone;
}

/**
 * Standard screen angles to avoid moiré
 */
enum HalftoneAngle {
    CYAN(15.0),
    MAGENTA(75.0),
    YELLOW(0.0),
    BLACK(45.0),
    SPOT_1(22.5),
    SPOT_2(67.5);

    final double degrees;
    HalftoneAngle(double degrees) { this.degrees = degrees; }
}

/**
 * Recommended LPI for different uses
 */
enum HalftoneQuality {
    COARSE(45, "Fast printing, soft hand, 110-160 mesh"),
    STANDARD(55, "Most common, good detail, 200-230 mesh"),
    FINE(65, "Maximum detail, requires 230+ mesh");

    final int lpi;
    final String description;
    HalftoneQuality(int lpi, String description) {
        this.lpi = lpi;
        this.description = description;
    }
}
```

**Usage**:
```bash
# Standard halftone photo print
--halftone-lpi=55 --halftone-mesh=230

# Coarse halftone for soft hand
--halftone-lpi=45 --halftone-mesh=160
```

---

### 6. **Registration Marks** ⭐⭐⭐⭐

**Current State**: No registration marks
**Need**: Add crosshairs/targets for screen alignment
**Priority**: HIGH - Essential for multi-color work

**Description**:
Registration marks are printed on each screen to help align them during printing. Without them, achieving proper registration is extremely difficult.

**Types of Registration Marks**:
1. **Crosshairs**: Simple + marks
2. **Bullseye**: Concentric circles
3. **Corner marks**: L-shaped corners
4. **Centering marks**: Center alignment aids

**Specifications**:
- **Size**: 1/4" to 1/2" typical
- **Location**: All four corners (minimum) or center + corners
- **Style**: High contrast (black on white or vice versa)
- **On every layer**: All separations must have identical marks

**Implementation**:
```java
/**
 * Adds registration marks to a layer
 */
private static void addRegistrationMarks(
    BufferedImage layer,
    RegistrationMarkType type,
    RegistrationMarkPosition[] positions
) {
    for (RegistrationMarkPosition pos : positions) {
        int x = calculateMarkX(layer.getWidth(), pos);
        int y = calculateMarkY(layer.getHeight(), pos);

        drawRegistrationMark(layer, x, y, type);
    }
}

enum RegistrationMarkType {
    CROSSHAIR,    // Simple +
    BULLSEYE,     // Concentric circles
    CORNER,       // L-shaped corner
    PRINTER_BAR   // Bar with marks
}

enum RegistrationMarkPosition {
    TOP_LEFT,
    TOP_RIGHT,
    BOTTOM_LEFT,
    BOTTOM_RIGHT,
    CENTER
}

/**
 * Draws a crosshair registration mark
 */
private static void drawCrosshair(
    BufferedImage layer,
    int centerX,
    int centerY,
    int size  // 1/4" typical = ~18 pixels at 72 DPI
) {
    Graphics2D g = layer.createGraphics();
    g.setColor(Color.BLACK);
    g.setStroke(new BasicStroke(1));

    // Horizontal line
    g.drawLine(centerX - size, centerY, centerX + size, centerY);

    // Vertical line
    g.drawLine(centerX, centerY - size, centerX, centerY + size);

    // Optional: Add circle around crosshair
    g.drawOval(centerX - size/2, centerY - size/2, size, size);

    g.dispose();
}
```

**Best Practices**:
- Place outside image area
- Add to bleed area (1/8" - 1/4" from edge)
- Include on every separation
- High contrast for visibility
- Consistent size across all screens

---

### 7. **Proper DPI for Screen Printing** ⭐⭐⭐⭐

**Current State**: Optimized for 3000 DPI (offset)
**Need**: 300-600 DPI is standard for screen printing
**Priority**: HIGH - Current DPI is overkill and slow

**Why Different**:
- **Film output**: 600 DPI typical
- **Direct-to-screen (DTS)**: 300-1200 DPI
- **Inkjet film**: 1440 DPI (but interpolated from 600)
- **3000 DPI**: Overkill for screen printing, causes:
  - Slow processing
  - Huge file sizes
  - No quality improvement
  - Wasted computation

**Recommended DPI**:
```
Use Case                DPI Setting
──────────────────────  ───────────
Standard film output    600 DPI
Budget film output      300 DPI
High-end DTS            1200 DPI
Typical DTS             720 DPI
Inkjet positive film    600-1440 DPI
```

**Implementation**:
```java
/**
 * Validates DPI for screen printing
 */
private static void validateDPI(int dpi, PrintingMethod method) {
    if (method == PrintingMethod.SCREEN_PRINT) {
        if (dpi > 1200) {
            System.out.println("═══════════════════════════════════════════════");
            System.out.println("WARNING: DPI > 1200 is unnecessary for screen printing");
            System.out.println("Recommended: 300-600 DPI for film output");
            System.out.println("Current: " + dpi + " DPI");
            System.out.println("Impact: Slower processing, larger files, no quality gain");
            System.out.println("═══════════════════════════════════════════════");
        }

        if (dpi < 300) {
            System.out.println("WARNING: DPI < 300 may result in poor quality");
            System.out.println("Minimum recommended: 300 DPI");
        }

        // Optimal range
        if (dpi >= 300 && dpi <= 600) {
            System.out.println("DPI setting optimal for screen printing: " + dpi);
        }
    }
}

/**
 * Suggests optimal DPI based on output method
 */
enum ScreenPrintOutput {
    FILM_STANDARD(600, "Standard film output (most common)"),
    FILM_BUDGET(300, "Budget film output (acceptable quality)"),
    DTS_STANDARD(720, "Direct-to-screen standard"),
    DTS_HIGH_END(1200, "Direct-to-screen high resolution"),
    INKJET_FILM(600, "Inkjet film positive (interpolated to 1440)");

    final int dpi;
    final String description;

    ScreenPrintOutput(int dpi, String description) {
        this.dpi = dpi;
        this.description = description;
    }
}
```

**Performance Impact**:
```
DPI    File Size    Processing Time    Quality Improvement
─────  ──────────   ────────────────   ───────────────────
300    Small        Fast               Acceptable
600    Medium       Moderate           Excellent
1200   Large        Slow               Marginal
3000   Huge         Very Slow          None
```

---

## 🎨 Priority 2: Color Separation Enhancements

### 8. **Spot Color Detection** ⭐⭐⭐⭐

**Current State**: RGB color separation
**Need**: Detect and separate Pantone/spot colors
**Priority**: HIGH - Screen printers primarily use spot colors

**Description**:
Screen printers typically use spot colors (discrete ink colors) rather than CMYK process colors. The tool should identify discrete colors in the design and separate them into individual screens.

**Difference from CMYK**:
- **CMYK**: Uses 4 colors (cyan, magenta, yellow, black) with halftones
- **Spot**: Uses exact ink colors (Pantone, custom mixes)
- **Screen printing**: Primarily spot colors (cheaper, brighter)

**Implementation**:
```java
/**
 * Detects discrete spot colors in the design
 */
private static Map<String, SpotColor> detectSpotColors(
    BufferedImage source,
    int maxColors  // Usually 4-6 for screen printing
) {
    // 1. Identify all unique RGB colors
    Map<Integer, Integer> colorFrequency = new HashMap<>();
    for (int y = 0; y < source.getHeight(); y++) {
        for (int x = 0; x < source.getWidth(); x++) {
            int rgb = source.getRGB(x, y) & 0x00FFFFFF;
            if ((source.getRGB(x, y) >> 24) > 0) {  // Not transparent
                colorFrequency.merge(rgb, 1, Integer::sum);
            }
        }
    }

    // 2. Sort by frequency
    List<Map.Entry<Integer, Integer>> sorted =
        colorFrequency.entrySet().stream()
            .sorted(Map.Entry.<Integer, Integer>comparingByValue().reversed())
            .limit(maxColors)
            .collect(Collectors.toList());

    // 3. Create spot color entries
    Map<String, SpotColor> spotColors = new LinkedHashMap<>();
    for (int i = 0; i < sorted.size(); i++) {
        int rgb = sorted.get(i).getKey();
        String name = suggestSpotColorName(rgb, i);
        spotColors.put(name, new SpotColor(name, rgb, i));
    }

    return spotColors;
}

/**
 * Suggests a spot color name based on RGB value
 */
private static String suggestSpotColorName(int rgb, int index) {
    int r = (rgb >> 16) & 0xFF;
    int g = (rgb >> 8) & 0xFF;
    int b = rgb & 0xFF;

    // Try to match to common screen printing colors
    if (r > 240 && g > 240 && b > 240) return "White";
    if (r < 20 && g < 20 && b < 20) return "Black";
    if (r > 200 && g < 100 && b < 100) return "Red";
    if (r < 100 && g < 100 && b > 200) return "Blue";
    if (r < 100 && g > 200 && b < 100) return "Green";
    if (r > 200 && g > 200 && b < 100) return "Yellow";

    // Generic name
    return String.format("Spot_%d_RGB(%d,%d,%d)", index + 1, r, g, b);
}

class SpotColor {
    String name;
    int rgb;
    int printOrder;
    String pantoneMatch;  // Optional: Closest Pantone color

    SpotColor(String name, int rgb, int printOrder) {
        this.name = name;
        this.rgb = rgb;
        this.printOrder = printOrder;
    }
}
```

**Output Example**:
```
Detected Spot Colors:
  1. White (RGB: 255,255,255) - 45% coverage
  2. Red (RGB: 235,50,35) - 25% coverage (closest: Pantone 485C)
  3. Blue (RGB: 30,85,200) - 20% coverage (closest: Pantone 286C)
  4. Black (RGB: 0,0,0) - 10% coverage
```

---

### 9. **Index Color Separation** ⭐⭐⭐

**Current State**: Not supported
**Need**: Limited color palette (4-6 colors max)
**Priority**: MEDIUM - Cost savings

**Description**:
Reduce a full-color image to a limited palette of 4-6 spot colors. This is essential for cost control in screen printing.

**Why Important**:
- Each color = one screen
- Screens cost $20-$50 each
- Setup time increases with colors
- 4-6 colors is sweet spot for cost vs. quality

**Implementation**:
```java
/**
 * Reduces image to index colors using median cut algorithm
 */
private static BufferedImage reduceToIndexColors(
    BufferedImage source,
    int maxColors  // typically 4-6
) {
    // Use median cut algorithm for color quantization
    // Returns image with limited palette
    return quantized;
}
```

---

### 10. **CMYK Separation Mode** ⭐⭐⭐

**Current State**: Discrete color separation only
**Need**: Option for process color (CMYK) separation
**Priority**: MEDIUM - Photo-realistic prints

**Use Case**:
- Photo-realistic prints using process colors
- Full-color photographs
- Gradients and continuous tone
- Usually combined with white underbase

**Implementation** (Basic):
```java
private static List<BufferedImage> separateToCMYK(
    BufferedImage source,
    boolean includeUnderbase
) {
    List<BufferedImage> layers = new ArrayList<>();

    if (includeUnderbase) {
        layers.add(generateUnderbase(source, 3, true));
    }

    // Convert RGB to CMYK
    BufferedImage cyan = extractChannel(source, CMYKChannel.CYAN);
    BufferedImage magenta = extractChannel(source, CMYKChannel.MAGENTA);
    BufferedImage yellow = extractChannel(source, CMYKChannel.YELLOW);
    BufferedImage black = extractChannel(source, CMYKChannel.BLACK);

    // Generate halftones for each
    layers.add(generateHalftone(cyan, 55, 15.0, 230, source.getDPI()));
    layers.add(generateHalftone(magenta, 55, 75.0, 230, source.getDPI()));
    layers.add(generateHalftone(yellow, 55, 0.0, 230, source.getDPI()));
    layers.add(generateHalftone(black, 55, 45.0, 230, source.getDPI()));

    return layers;
}
```

---

## 🛠️ Priority 3: Production Features

### 11. **Garment Color Specification** ⭐⭐⭐⭐

**Current State**: Assumes white background
**Need**: Specify garment color
**Priority**: HIGH - Affects underbase decisions

**Implementation**:
```java
enum GarmentColor {
    WHITE("No underbase needed", false),
    LIGHT("Minimal underbase for bright colors", false),
    MEDIUM("Partial underbase", true),
    DARK("Full white underbase", true),
    BLACK("Heavy underbase, may need gray base", true);

    final String description;
    final boolean needsUnderbase;

    GarmentColor(String description, boolean needsUnderbase) {
        this.description = description;
        this.needsUnderbase = needsUnderbase;
    }
}

private static boolean needsUnderbase(
    int designColor,
    GarmentColor garmentColor
) {
    if (!garmentColor.needsUnderbase) {
        return false;
    }

    // Check if design color is light enough to need underbase
    double lightness = calculateLightness(designColor);

    return lightness > 100;  // Light colors need underbase on dark garments
}
```

---

### 12. **Print Order Specification** ⭐⭐⭐

**Current State**: Fixed order (light to dark)
**Need**: Flexible print sequence
**Priority**: MEDIUM

**Screen Printing Order**:
1. White underbase (if dark garment)
2. Light colors
3. Mid tones
4. Dark colors
5. Black (top layer, highest opacity)

**Implementation**:
```java
private static List<LayerData> orderForScreenPrinting(
    List<LayerData> layers,
    boolean hasUnderbase
) {
    List<LayerData> ordered = new ArrayList<>();

    // Underbase always first
    if (hasUnderbase) {
        LayerData underbase = layers.stream()
            .filter(l -> l.name.contains("Underbase"))
            .findFirst()
            .orElse(null);
        if (underbase != null) {
            ordered.add(underbase);
        }
    }

    // Sort remaining by lightness (light to dark)
    layers.stream()
        .filter(l -> !l.name.contains("Underbase"))
        .sorted((a, b) -> Double.compare(
            calculateLayerLightness(b),
            calculateLayerLightness(a)
        ))
        .forEach(ordered::add);

    return ordered;
}
```

---

### 13. **Film Positive Output** ⭐⭐⭐⭐

**Current State**: PSD output only
**Need**: High-contrast black & white for film output
**Priority**: HIGH

**Description**:
Screen printers burn screens using film positives (or negatives). These need to be pure black and white (1-bit) with no grays.

**Requirements**:
- Pure black (0, 0, 0) and pure white (255, 255, 255)
- No anti-aliasing or grays
- High resolution (600 DPI typical)
- Can be inverted (negative) if needed
- Usually output as TIFF or PDF

**Implementation**:
```java
/**
 * Creates film positive output (pure black & white)
 */
private static BufferedImage createFilmPositive(
    BufferedImage layer,
    boolean invert  // Some processes need negative
) {
    BufferedImage film = new BufferedImage(
        layer.getWidth(),
        layer.getHeight(),
        BufferedImage.TYPE_BYTE_BINARY  // 1-bit black & white
    );

    for (int y = 0; y < layer.getHeight(); y++) {
        for (int x = 0; x < layer.getWidth(); x++) {
            int pixel = layer.getRGB(x, y);
            int alpha = (pixel >> 24) & 0xFF;

            // Convert to pure black or white
            boolean isInk = alpha > 127;  // Threshold at 50%

            if (invert) {
                isInk = !isInk;
            }

            film.setRGB(x, y, isInk ? 0xFF000000 : 0xFFFFFFFF);
        }
    }

    return film;
}

/**
 * Outputs each layer as separate TIFF file for film
 */
private static void outputFilmPositives(
    List<LayerData> layers,
    String outputDir,
    boolean invert
) throws IOException {
    for (int i = 0; i < layers.size(); i++) {
        BufferedImage film = createFilmPositive(layers.get(i).image, invert);

        String filename = String.format(
            "%s/film_%02d_%s.tif",
            outputDir,
            i + 1,
            sanitizeFilename(layers.get(i).name)
        );

        // Write as 1-bit TIFF
        ImageIO.write(film, "TIFF", new File(filename));

        System.out.println("Created film positive: " + filename);
    }
}
```

---

### 14. **Mesh Count Recommendations** ⭐⭐⭐

**Current State**: No mesh guidance
**Need**: Suggest appropriate mesh counts
**Priority**: MEDIUM

**Implementation**:
```java
/**
 * Recommends mesh count based on layer type
 */
private static int recommendMeshCount(
    LayerType type,
    int lpi  // if halftone
) {
    switch (type) {
        case UNDERBASE:
            return 110;  // Low mesh for thick white deposit

        case SOLID_COLOR:
            return 160;  // Standard for solid spot colors

        case FINE_DETAIL:
            return 200;  // Higher mesh for fine lines

        case HALFTONE:
            return Math.max(230, lpi * 4);  // 4x LPI rule

        default:
            return 160;  // Safe default
    }
}

enum LayerType {
    UNDERBASE,
    SOLID_COLOR,
    FINE_DETAIL,
    HALFTONE
}
```

**Output Example**:
```
Screen Recommendations:
  Screen 1: White Underbase - 110 mesh, 2pt choke
  Screen 2: Red (Spot 1) - 160 mesh, solid coverage
  Screen 3: Blue (Spot 2) - 160 mesh, solid coverage
  Screen 4: Black - 200 mesh, fine detail, 4pt trap
```

---

### 15. **Separate File Per Layer** ⭐⭐⭐⭐

**Current State**: Single PSD file
**Need**: Individual files for each screen
**Priority**: HIGH

**Why**: Easier to send to screen maker or RIP

**Implementation**:
```java
/**
 * Outputs each layer as separate file
 */
private static void outputSeparateFiles(
    List<LayerData> layers,
    String outputDir,
    OutputFormat format  // PNG, TIFF, PDF
) throws IOException {
    for (int i = 0; i < layers.size(); i++) {
        String filename = String.format(
            "%s/%02d_%s.%s",
            outputDir,
            i + 1,
            sanitizeFilename(layers.get(i).name),
            format.extension
        );

        // Save file
        saveLayerAsFile(layers.get(i), filename, format);

        System.out.println("Created: " + filename);
    }
}

enum OutputFormat {
    PNG("png"),
    TIFF("tif"),
    PDF("pdf");

    final String extension;
    OutputFormat(String extension) { this.extension = extension; }
}
```

**Output Example**:
```
output/
  01_White_Underbase.tif
  02_Red_Spot1.tif
  03_Blue_Spot2.tif
  04_Black.tif
```

---

### 16. **Separation Report** ⭐⭐⭐

**Current State**: Console output only
**Need**: Detailed separation report
**Priority**: MEDIUM

**Contents**:
```
SCREEN PRINTING SEPARATION REPORT
═══════════════════════════════════════════

Design: logo_design.psd
Date: 2025-12-31
Garment: Dark (Black)

SEPARATIONS
───────────────────────────────────────────
  Screen 1: White Underbase
    - Mesh: 110
    - Choke: 3 points (0.042")
    - Coverage: 75% of design
    - Print order: 1 (first)

  Screen 2: Red (Pantone 485C)
    - Mesh: 160
    - Trap: 0 points (no trap)
    - Coverage: 30% of design
    - Print order: 2

  Screen 3: Blue (Pantone 286C)
    - Mesh: 160
    - Trap: 0 points (no trap)
    - Coverage: 25% of design
    - Print order: 3

  Screen 4: Black
    - Mesh: 200
    - Trap: 4 points (0.056")
    - Coverage: 15% of design
    - Print order: 4 (last)

REGISTRATION
───────────────────────────────────────────
  - Trap tolerance: 4 points (standard)
  - Registration marks: Crosshairs (4 corners)
  - Bleed: 0.125" (1/8")

PRODUCTION NOTES
───────────────────────────────────────────
  - Total screens: 4
  - Estimated setup cost: $120-$200
  - Recommended press: 4-color manual or automatic
  - Flash cure: After underbase
  - Estimated print time: 45-60 seconds per shirt

FILES GENERATED
───────────────────────────────────────────
  01_White_Underbase.tif
  02_Red_Pantone485C.tif
  03_Blue_Pantone286C.tif
  04_Black.tif
```

---

## 🎓 Priority 4: User Experience

### 17. **Screen Printing Presets** ⭐⭐⭐⭐

**Current State**: Manual parameter entry
**Need**: Common screen printing presets
**Priority**: HIGH - Improves usability

**Implementation**:
```java
enum ScreenPrintPreset {
    STANDARD_DARK_GARMENT {
        public SeparationSettings getSettings() {
            return new SeparationSettings()
                .setTrapSize(4, "pt")
                .setUnderbase(true, 3, "pt")  // 3pt choke
                .setGarmentColor(GarmentColor.DARK)
                .setPrintingMethod(PrintingMethod.SCREEN_PRINT)
                .setOutputDPI(600);
        }
    },

    TIGHT_REGISTRATION {
        public SeparationSettings getSettings() {
            return new SeparationSettings()
                .setTrapSize(2, "pt")
                .setUnderbase(true, 2, "pt")
                .setGarmentColor(GarmentColor.DARK)
                .setPrintingMethod(PrintingMethod.SCREEN_PRINT)
                .setOutputDPI(600);
        }
    },

    HALFTONE_PHOTO {
        public SeparationSettings getSettings() {
            return new SeparationSettings()
                .setHalftone(true, 55, 230)  // 55 LPI, 230 mesh
                .setUnderbase(true, 3, "pt")
                .setGarmentColor(GarmentColor.DARK)
                .setPrintingMethod(PrintingMethod.SCREEN_PRINT)
                .setOutputDPI(600);
        }
    },

    SIMPLE_SPOT_COLOR {
        public SeparationSettings getSettings() {
            return new SeparationSettings()
                .setTrapSize(4, "pt")
                .setUnderbase(false)
                .setGarmentColor(GarmentColor.WHITE)
                .setPrintingMethod(PrintingMethod.SCREEN_PRINT)
                .setOutputDPI(300);  // Lower DPI for simple designs
        }
    },

    LOOSE_REGISTRATION {
        public SeparationSettings getSettings() {
            return new SeparationSettings()
                .setTrapSize(6, "pt")
                .setUnderbase(true, 4, "pt")
                .setGarmentColor(GarmentColor.DARK)
                .setPrintingMethod(PrintingMethod.SCREEN_PRINT)
                .setOutputDPI(600);
        }
    };

    public abstract SeparationSettings getSettings();
}

class SeparationSettings {
    // Builder pattern for settings
    // Allows chaining: .setTrapSize(4).setUnderbase(true)
}
```

**Usage**:
```bash
# Use preset
./gradlew runColorSeparator -PpsdFile=design.psd -Ppreset=STANDARD_DARK_GARMENT

# List available presets
./gradlew runColorSeparator --list-presets
```

---

### 18. **Minimize Screen Count** ⭐⭐⭐⭐

**Current State**: Separates all colors
**Need**: Suggest color reduction
**Priority**: HIGH - Cost optimization

**Why**: Each screen costs $20-$50 to make

**Implementation**:
```java
private static void analyzeScreenCount(List<LayerData> layers) {
    int screenCount = layers.size();

    if (screenCount > 6) {
        System.out.println("╔══════════════════════════════════════════════╗");
        System.out.println("║  COST WARNING: High Screen Count            ║");
        System.out.println("╠══════════════════════════════════════════════╣");
        System.out.println("║  Current: " + screenCount + " screens                         ║");
        System.out.println("║  Recommended: 4-6 screens maximum           ║");
        System.out.println("║                                              ║");
        System.out.println("║  Cost Impact:                                ║");
        System.out.println("║    Setup: $" + (screenCount * 30) + " - $" + (screenCount * 50) + "                 ║");
        System.out.println("║    Print time: " + (screenCount * 12) + " seconds/shirt        ║");
        System.out.println("║                                              ║");
        System.out.println("║  Suggestions:                                ║");
        System.out.println("║    - Merge similar colors                    ║");
        System.out.println("║    - Remove low-coverage colors              ║");
        System.out.println("║    - Use simulated process (6-8 colors)      ║");
        System.out.println("╚══════════════════════════════════════════════╝");
    } else if (screenCount <= 4) {
        System.out.println("✓ Screen count optimal: " + screenCount + " screens");
        System.out.println("  Estimated setup: $" + (screenCount * 30) + " - $" + (screenCount * 50));
    }
}
```

---

## 🎯 Implementation Roadmap

### Phase 1: Core Functionality (Week 1-2) ⭐⭐⭐⭐⭐

**Goal**: Make tool usable for basic screen printing

1. **Underbase Generation** (#1)
   - Generate white underbase layer
   - Choke underbase by specified amount
   - Add as first layer in output

2. **Reverse Trap Direction** (#2)
   - Add PrintingMethod enum (OFFSET vs SCREEN_PRINT)
   - Reverse expansion calculation for screen printing
   - Darkest colors get trap instead of lightest

3. **Screen Printing Trap Sizes** (#3)
   - Add point-based parsing ("2pt", "4pt")
   - Update defaults (0 to 4pt)
   - Add presets (tight/standard/loose)

4. **Proper DPI Validation** (#7)
   - Warn if DPI > 1200
   - Suggest 300-600 DPI
   - Add DPI presets for film/DTS

**Deliverable**: Tool can generate separations suitable for screen printing on dark garments

---

### Phase 2: Production Features (Week 3-4) ⭐⭐⭐⭐

**Goal**: Professional output quality

5. **Registration Marks** (#6)
   - Add crosshair marks to all layers
   - Place in corners and center
   - Configurable size and style

6. **Film Positive Output** (#13)
   - Convert layers to 1-bit black & white
   - Output as TIFF files
   - Support positive/negative

7. **Separate Files Per Layer** (#15)
   - Output each layer as individual file
   - PNG/TIFF format options
   - Proper file naming

8. **Garment Color Specification** (#11)
   - Add garment color parameter
   - Auto-determine underbase need
   - Adjust colors for garment

**Deliverable**: Production-ready film separations

---

### Phase 3: Advanced Features (Week 5-6) ⭐⭐⭐

**Goal**: Professional features and halftones

9. **Halftone Support** (#5)
   - Generate halftone dots (45-65 LPI)
   - Screen angle support
   - Mesh count validation

10. **Spot Color Detection** (#8)
    - Auto-detect discrete colors
    - Name colors intelligently
    - Suggest Pantone matches

11. **Screen Printing Presets** (#17)
    - Standard dark garment
    - Tight registration
    - Halftone photo
    - Simple spot color

12. **Separation Report** (#16)
    - Detailed separation info
    - Mesh recommendations
    - Cost estimates
    - Production notes

**Deliverable**: Professional-grade separations with halftones

---

### Phase 4: Polish & UX (Week 7-8) ⭐⭐

**Goal**: Easy to use, well-documented

13. **Screen Count Warnings** (#18)
    - Warn if > 6 screens
    - Show cost impact
    - Suggest optimizations

14. **Mesh Count Recommendations** (#14)
    - Recommend mesh per layer
    - Based on layer type
    - Include in report

15. **Documentation**
    - Update README for screen printing
    - Add examples
    - Create tutorials

16. **Testing**
    - Test with real designs
    - Validate output with screen printers
    - Fix edge cases

**Deliverable**: Polished, user-friendly tool

---

## 📊 Feature Prioritization Matrix

| Feature | Impact | Effort | Priority |
|---------|--------|--------|----------|
| Underbase Generation | CRITICAL | Medium | ⭐⭐⭐⭐⭐ |
| Reverse Trap Direction | CRITICAL | Low | ⭐⭐⭐⭐⭐ |
| Screen Trap Sizes | CRITICAL | Low | ⭐⭐⭐⭐⭐ |
| Choke Underbase | CRITICAL | Low | ⭐⭐⭐⭐⭐ |
| Registration Marks | High | Medium | ⭐⭐⭐⭐ |
| Film Output | High | Low | ⭐⭐⭐⭐ |
| Proper DPI | High | Low | ⭐⭐⭐⭐ |
| Garment Color | High | Low | ⭐⭐⭐⭐ |
| Halftone Support | High | High | ⭐⭐⭐⭐ |
| Spot Color Detection | High | Medium | ⭐⭐⭐⭐ |
| Screen Presets | Medium | Low | ⭐⭐⭐⭐ |
| Separate Files | Medium | Low | ⭐⭐⭐⭐ |
| Mesh Recommendations | Medium | Low | ⭐⭐⭐ |
| Screen Count Warning | Medium | Low | ⭐⭐⭐⭐ |
| Separation Report | Medium | Medium | ⭐⭐⭐ |
| CMYK Mode | Low | High | ⭐⭐⭐ |
| Index Colors | Low | Medium | ⭐⭐⭐ |
| Simulated Process | Low | Very High | ⭐⭐ |

---

## 🎓 Key Takeaways

### Current Tool Assessment:
- ✅ **Solid foundation**: Good image processing, RLE compression, parallel processing
- ❌ **Wrong direction**: Traps lighter into darker (opposite of screen printing)
- ❌ **Missing essentials**: No underbase, no choke, no halftones
- ⚠️ **Wrong scale**: Trap sizes too small (1/32" vs 4pt typical)
- ⚠️ **Wrong DPI**: 3000 DPI overkill (600 DPI sufficient)
- ⚠️ **Wrong units**: Fractional inches vs points

### Essential Changes:
1. **Reverse trap direction** (darkest gets trap, not lightest)
2. **Add underbase generation** with choke
3. **Increase trap sizes** (2-6 points standard)
4. **Add point-based measurements**
5. **Lower default DPI** (300-600)
6. **Add registration marks**
7. **Film output format** (1-bit TIFF)

### Tool Positioning:
- **Current**: Excellent offset lithography tool
- **Potential**: First open-source screen printing separator
- **Market**: Even bigger need (more screen printers than offset shops)
- **Competition**: T-Seps ($300), Separation Studio ($500), etc.

---

## 📚 References

### Screen Printing Resources:
- [T-Shirt Forums - Choke and Spread](https://www.t-shirtforums.com/threads/choke-and-spread.118859/)
- [Impressions Magazine - Your Underbase is Showing](https://impressionsmagazine.com/screen-printing/graphics-design/your-underbase-is-showing/)
- [ScreenPrinting.com - Underbase Guide](https://www.screenprinting.com/blogs/news/everything-you-need-to-know-to-screen-print-an-underbase)
- [Anatol Equipment - Underbase Tricky Business](https://anatol.com/the-tricky-business-of-screen-printing-underbase/)

### Technical Specifications:
- **Trap Sizes**: 2-6 points (0.028" - 0.083")
- **Underbase Choke**: 2-4 points
- **Halftone LPI**: 45-65 LPI
- **Mesh Counts**: 110-230
- **Film DPI**: 300-600 typical
- **Screen Angles**: 0°, 22.5°, 45°, 67.5° (avoid moiré)

### Industry Standards:
- **Maximum colors**: 4-6 for cost-effectiveness
- **Registration tolerance**: 2-6 points
- **Underbase mesh**: 110-160
- **Fine detail mesh**: 200-230
- **Halftone rule**: Mesh count ≥ 4× LPI

---

**Document Version**: 1.0
**Last Updated**: December 31, 2025
**Author**: Analysis for Trapper screen printing enhancements
**Status**: Ready for implementation

---

## 🚀 Next Steps

1. **Review this document** with users/stakeholders
2. **Prioritize features** based on immediate needs
3. **Start with Phase 1** (core functionality)
4. **Test early and often** with real screen printing jobs
5. **Iterate based on feedback** from actual screen printers

**The tool has excellent bones - it just needs screen printing DNA!** 🎨👕
