package org.electrosaur.trapper;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.stream.ImageInputStream;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Reads a PSD file, analyzes its colors, and creates a color-separated
 * multi-layer PSD where each layer contains pixels of a single color,
 * ordered from lightest to darkest.
 */
public class PsdColorSeparator {

    private static final int MAX_COLORS = 10;

    // Default expansion range in inches
    private static final double DEFAULT_MIN_EXPANSION = 0.0;      // Darkest layer
    private static final double DEFAULT_MAX_EXPANSION = 1.0 / 32.0; // Lightest layer (1/32")

    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java PsdColorSeparator <input.psd> [minTrap] [maxTrap] [mode]");
            System.err.println("  minTrap: minimum trap size (darkest layer, default: 0)");
            System.err.println("  maxTrap: maximum trap size (lightest layer, default: 1/32 for offset, 4pt for screen)");
            System.err.println("  mode: trapping mode (default: offset)");
            System.err.println("    - offset: Offset lithography (light spreads into dark)");
            System.err.println("    - screen: Screen printing (dark traps over light)");
            System.err.println("  Trap sizes can be specified as:");
            System.err.println("    - Fractions: 1/32, 1/64, 1/16, etc.");
            System.err.println("    - Decimals: 0.03125, 0.015625, etc.");
            System.err.println("    - Points: 2pt, 4pt, 6pt (screen printing)");
            System.exit(1);
        }

        String inputFile = args[0];
        String outputFile = generateOutputFilename(inputFile);

        // Parse mode argument (optional, defaults to "offset")
        String mode = "offset";
        if (args.length >= 4) {
            mode = args[3].toLowerCase();
            if (!mode.equals("offset") && !mode.equals("screen")) {
                System.err.println("Error: mode must be 'offset' or 'screen'");
                System.exit(1);
            }
        }

        // Select trapping strategy based on mode
        TrappingStrategy strategy;
        if (mode.equals("screen")) {
            strategy = new ScreenPrintingTrappingStrategy();
        } else {
            strategy = new OffsetTrappingStrategy();
        }

        // Mode-specific default trap sizes
        double defaultMinTrap = 0.0;
        double defaultMaxTrap;
        if (mode.equals("screen")) {
            defaultMaxTrap = 4.0 / 72.0;  // 4 points in inches
        } else {
            defaultMaxTrap = DEFAULT_MAX_EXPANSION; // 1/32 inch
        }

        // Parse optional trap size arguments
        double minExpansion = defaultMinTrap;
        double maxExpansion = defaultMaxTrap;

        if (args.length >= 3) {
            try {
                minExpansion = parseTrapSize(args[1]);
                maxExpansion = parseTrapSize(args[2]);

                if (minExpansion < 0 || maxExpansion < 0) {
                    System.err.println("Error: Trap sizes must be non-negative");
                    System.exit(1);
                }
                if (minExpansion > maxExpansion) {
                    System.err.println("Error: minTrap must be <= maxTrap");
                    System.exit(1);
                }
            } catch (IllegalArgumentException e) {
                System.err.println("Error: " + e.getMessage());
                System.exit(1);
            }
        }

        try {
            processFile(inputFile, outputFile, minExpansion, maxExpansion, strategy);
            System.out.println("Successfully created color-separated PSD: " + outputFile);
        } catch (Exception e) {
            System.err.println("Error processing PSD file: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    /**
     * Parses a trap size specification in fractional (1/32), decimal (0.03125), or point (4pt) format
     */
    private static double parseTrapSize(String spec) {
        spec = spec.trim();

        // Check if it's a point specification (e.g., "2pt", "4pt", "6pt")
        if (spec.toLowerCase().endsWith("pt")) {
            try {
                String pointStr = spec.substring(0, spec.length() - 2).trim();
                double points = Double.parseDouble(pointStr);
                if (points < 0) {
                    throw new IllegalArgumentException("Points must be non-negative: " + spec);
                }
                // Convert points to inches: 72 points = 1 inch
                return points / 72.0;
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid point format: " + spec + ". Use format like 2pt or 4pt");
            }
        }

        // Check if it's a fraction (e.g., "1/32")
        if (spec.contains("/")) {
            String[] parts = spec.split("/");
            if (parts.length != 2) {
                throw new IllegalArgumentException("Invalid fraction format: " + spec + ". Use format like 1/32");
            }
            try {
                double numerator = Double.parseDouble(parts[0].trim());
                double denominator = Double.parseDouble(parts[1].trim());
                if (denominator == 0) {
                    throw new IllegalArgumentException("Division by zero in fraction: " + spec);
                }
                return numerator / denominator;
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid fraction format: " + spec + ". Use numbers like 1/32");
            }
        } else {
            // Parse as decimal
            try {
                return Double.parseDouble(spec);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid decimal format: " + spec + ". Use format like 0.03125, 1/32, or 4pt");
            }
        }
    }

    /**
     * Generates output filename in format: out-<NAME>.psd
     * If input is in-<NAME>.psd, output will be out-<NAME>.psd
     * Otherwise, output will be out-<input_filename>.psd
     */
    private static String generateOutputFilename(String inputFile) {
        File file = new File(inputFile);
        String name = file.getName();
        String parent = file.getParent();

        // Remove .psd extension if present
        if (name.toLowerCase().endsWith(".psd")) {
            name = name.substring(0, name.length() - 4);
        }

        String outputName;
        // If input starts with "in-", replace with "out-"
        if (name.startsWith("in-")) {
            outputName = "out-" + name.substring(3) + ".psd";
        } else {
            outputName = "out-" + name + ".psd";
        }

        return parent != null ? parent + File.separator + outputName : outputName;
    }

    /**
     * Main processing logic
     */
    public static void processFile(String inputFile, String outputFile,
                                   double minExpansion, double maxExpansion,
                                   TrappingStrategy strategy) throws IOException {
        // Step 1: Read the single-layer PSD, get DPI and existing metadata
        PsdInfo psdInfo = readAndFlattenPSD(inputFile);
        BufferedImage sourceImage = psdInfo.image;
        int dpi = psdInfo.dpi;
        String originalMetadata = psdInfo.copyrightMetadata;

        System.out.println("Image DPI: " + dpi);
        System.out.println("Trapping mode: " + strategy.getName());
        System.out.println("Trap direction: " + strategy.getTrapDirection());
        System.out.printf("Trap range: %.6f\" to %.6f\" (1/%d\" to 1/%d\")%n",
            minExpansion, maxExpansion,
            minExpansion > 0 ? (int)Math.round(1.0 / minExpansion) : 0,
            maxExpansion > 0 ? (int)Math.round(1.0 / maxExpansion) : 0);

        // Step 2: Count distinct colors (ignoring transparent pixels)
        Map<Integer, Integer> colorCounts = countColors(sourceImage);

        // Step 3: Check color count
        if (colorCounts.size() > MAX_COLORS) {
            throw new IllegalStateException(
                String.format("Image has %d distinct colors, exceeds maximum of %d",
                    colorCounts.size(), MAX_COLORS));
        }

        System.out.println("Found " + colorCounts.size() + " distinct colors");

        // Step 4: Sort colors by lightness (light to dark)
        List<Integer> sortedColors = sortColorsByLightness(colorCounts.keySet());

        // Step 5: Create output PSD with color-separated layers and trapping
        List<LayerData> layers = createColorSeparatedLayers(sourceImage, sortedColors, dpi,
                                                            minExpansion, maxExpansion, strategy);

        // Step 6: Verify that flattening the trapped layers matches the original
        System.out.println("Verifying trapped output...");
        int width = sourceImage.getWidth();
        int height = sourceImage.getHeight();
        BufferedImage trappedFlattened = flattenLayers(layers, width, height);
        verifyFlattening(sourceImage, trappedFlattened);

        // Step 7: Write the PSD file with preserved metadata
        System.out.println("Writing output PSD file...");
        writePSD(outputFile, width, height, layers, originalMetadata);
        System.out.println("Output file written.");
    }

    /**
     * Flattens layers by compositing them from lightest to darkest
     * This simulates how the layers will look when printed in order
     */
    private static BufferedImage flattenLayers(List<LayerData> layers, int width, int height) {
        BufferedImage result = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = result.createGraphics();

        // Start with transparent/black background
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, width, height);

        // Composite layers from lightest to darkest (order they're in the list)
        // Each layer should only paint where it has non-transparent pixels
        for (LayerData layer : layers) {
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int pixel = layer.image.getRGB(x, y);
                    int alpha = (pixel >> 24) & 0xFF;

                    // If pixel is opaque, overwrite the result
                    if (alpha > 0) {
                        result.setRGB(x, y, pixel & 0x00FFFFFF);
                    }
                }
            }
        }

        g.dispose();
        return result;
    }

    /**
     * Verifies that the flattened trapped image matches the original
     * In offset lithography mode, lighter colors are allowed to trap over darker colors,
     * so we verify that each original pixel is covered by either its original color
     * or a lighter color (which indicates successful trapping).
     */
    private static void verifyFlattening(BufferedImage original, BufferedImage flattened) {
        int width = original.getWidth();
        int height = original.getHeight();
        int differences = 0;
        int trappedPixels = 0;  // Pixels where a lighter color trapped over darker
        Map<String, Integer> invalidChanges = new HashMap<>();

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int origRgb = original.getRGB(x, y) & 0x00FFFFFF;
                int flatRgb = flattened.getRGB(x, y) & 0x00FFFFFF;

                if (origRgb != flatRgb) {
                    // Check if this is a valid trap (lighter color over darker)
                    double origLightness = calculateLightness(origRgb);
                    double flatLightness = calculateLightness(flatRgb);

                    if (flatLightness > origLightness) {
                        // This is expected: lighter color trapped over darker color
                        trappedPixels++;
                    } else {
                        // This is unexpected: darker color where lighter color should be
                        differences++;
                        int r = (origRgb >> 16) & 0xFF;
                        int g = (origRgb >> 8) & 0xFF;
                        int b = origRgb & 0xFF;
                        String colorKey = String.format("RGB(%d,%d,%d) replaced by RGB(%d,%d,%d)",
                            r, g, b,
                            (flatRgb >> 16) & 0xFF,
                            (flatRgb >> 8) & 0xFF,
                            flatRgb & 0xFF);
                        invalidChanges.put(colorKey, invalidChanges.getOrDefault(colorKey, 0) + 1);
                    }
                }
            }
        }

        if (differences > 0) {
            System.err.println("ERROR: Flattened trapped image has invalid color changes!");
            System.err.println("Number of invalid pixels: " + differences + " out of " + (width * height));
            System.err.println("Invalid color changes (should not happen):");
            for (Map.Entry<String, Integer> entry : invalidChanges.entrySet()) {
                System.err.println("  " + entry.getKey() + ": " + entry.getValue() + " pixels");
            }
        } else {
            System.out.println("Verification passed: Flattened trapped image matches original");
            if (trappedPixels > 0) {
                System.out.printf("  (%d pixels successfully trapped with lighter colors)%n", trappedPixels);
            }
        }
    }

    /**
     * Helper class to hold PSD info
     */
    private static class PsdInfo {
        BufferedImage image;
        int dpi;
        String copyrightMetadata;

        PsdInfo(BufferedImage image, int dpi, String copyrightMetadata) {
            this.image = image;
            this.dpi = dpi;
            this.copyrightMetadata = copyrightMetadata;
        }
    }

    /**
     * Reads a single-layer PSD file
     * Validates that exactly one layer exists
     * Also extracts DPI information from the file
     */
    private static PsdInfo readAndFlattenPSD(String filename) throws IOException {
        File file = new File(filename);

        // Use ImageIO with TwelveMonkeys PSD support
        try (ImageInputStream iis = ImageIO.createImageInputStream(file)) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);

            if (!readers.hasNext()) {
                throw new IOException("No reader found for PSD file");
            }

            ImageReader reader = readers.next();
            reader.setInput(iis);

            // Validate that PSD has exactly one layer
            int numImages = reader.getNumImages(true);
            if (numImages != 1) {
                throw new IllegalArgumentException(
                    String.format("PSD file must have exactly 1 layer, but has %d layers.\n" +
                        "Please flatten layers before processing:\n" +
                        "  In Photoshop: Layer > Flatten Image (or press Ctrl+E)", numImages));
            }

            // Read the single layer
            BufferedImage image = reader.read(0);

            // Try to extract DPI from metadata
            int dpi = 72; // Default DPI if not found
            try {
                IIOMetadata metadata = reader.getImageMetadata(0);
                if (metadata != null) {
                    dpi = extractDPI(metadata);
                }
            } catch (Exception e) {
                System.out.println("Could not read DPI from metadata, using default: " + dpi);
            }

            // Try to extract XMP metadata from Image Resources
            String copyrightMetadata = extractXMPFromPSD(file);
            if (copyrightMetadata != null) {
                System.out.println("Found existing XMP metadata in input file");
            }

            reader.dispose();

            System.out.println("Read PSD: " + image.getWidth() + "x" + image.getHeight());
            return new PsdInfo(image, dpi, copyrightMetadata);
        }
    }

    /**
     * Extracts XMP metadata (Resource 1060) from a PSD file
     * Returns the XMP string or null if not found
     */
    private static String extractXMPFromPSD(File psdFile) {
        try (RandomAccessFile raf = new RandomAccessFile(psdFile, "r");
             FileChannel channel = raf.getChannel()) {

            // Read file header (26 bytes)
            ByteBuffer header = ByteBuffer.allocate(26);
            int bytesRead = channel.read(header);
            if (bytesRead < 26) {
                return null;
            }
            header.flip();

            // Check signature
            byte[] sig = new byte[4];
            header.get(sig);
            if (!new String(sig).equals("8BPS")) {
                return null;
            }

            // Skip version (2) and reserved (6)
            header.position(header.position() + 8);

            // Skip file info (14 bytes: channels, height, width, bits, color mode)
            header.position(header.position() + 14);

            // Read Color Mode Data Section length and skip it
            ByteBuffer colorModeLength = ByteBuffer.allocate(4);
            channel.read(colorModeLength);
            colorModeLength.flip();
            int colorModeDataLen = colorModeLength.getInt();
            channel.position(channel.position() + colorModeDataLen);

            // Read Image Resources Section
            ByteBuffer resourcesLength = ByteBuffer.allocate(4);
            channel.read(resourcesLength);
            resourcesLength.flip();
            int resourcesLen = resourcesLength.getInt();

            if (resourcesLen == 0) {
                return null;
            }

            // Read all resources
            ByteBuffer resourcesData = ByteBuffer.allocate(resourcesLen);
            channel.read(resourcesData);
            resourcesData.flip();

            // Parse resources looking for 1060 (XMP metadata)
            while (resourcesData.remaining() >= 12) {
                byte[] resSig = new byte[4];
                resourcesData.get(resSig);
                String sigStr = new String(resSig, "ISO-8859-1");
                if (!sigStr.equals("8BIM")) {
                    break;
                }

                int resourceId = resourcesData.getShort() & 0xFFFF;

                // Read name (Pascal string or short 0)
                int nameField = resourcesData.getShort() & 0xFFFF;

                if (resourcesData.remaining() < 4) {
                    break;
                }

                int dataSize = resourcesData.getInt();

                if (resourcesData.remaining() < dataSize) {
                    break;
                }

                if (resourceId == 1060) {
                    // Found XMP metadata
                    byte[] data = new byte[dataSize];
                    resourcesData.get(data);
                    return new String(data, "UTF-8").trim();
                } else {
                    // Skip this resource's data
                    resourcesData.position(resourcesData.position() + dataSize);
                }

                // Resources are padded to even byte boundaries
                if ((dataSize & 1) == 1 && resourcesData.hasRemaining()) {
                    resourcesData.get();
                }
            }

            return null;
        } catch (Exception e) {
            // If we can't read metadata, just return null and continue
            return null;
        }
    }

    /**
     * Extracts DPI from image metadata
     */
    private static int extractDPI(IIOMetadata metadata) {
        // Try standard metadata format
        String[] formatNames = metadata.getMetadataFormatNames();
        for (String formatName : formatNames) {
            Node root = metadata.getAsTree(formatName);
            int dpi = searchDPIInNode(root);
            if (dpi > 0) {
                return dpi;
            }
        }
        return 72; // Default if not found
    }

    /**
     * Recursively searches for DPI information in metadata nodes
     */
    private static int searchDPIInNode(Node node) {
        // Look for standard dimension nodes
        if (node.getNodeName().equals("HorizontalPixelSize")) {
            NamedNodeMap attributes = node.getAttributes();
            if (attributes != null) {
                Node valueNode = attributes.getNamedItem("value");
                if (valueNode != null) {
                    // HorizontalPixelSize is in mm, convert to DPI
                    double pixelSizeMM = Double.parseDouble(valueNode.getNodeValue());
                    return (int) Math.round(25.4 / pixelSizeMM);
                }
            }
        }

        // Check attributes for resolution info
        NamedNodeMap attributes = node.getAttributes();
        if (attributes != null) {
            for (int i = 0; i < attributes.getLength(); i++) {
                Node attr = attributes.item(i);
                String name = attr.getNodeName();
                if (name.contains("Resolution") || name.contains("DPI") || name.contains("dpi")) {
                    try {
                        String value = attr.getNodeValue();
                        // Parse as double first to handle decimals like "300.0"
                        double dpiDouble = Double.parseDouble(value.replaceAll("[^0-9.]", ""));
                        return (int) Math.round(dpiDouble);
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
        }

        // Recursively search children
        for (Node child = node.getFirstChild(); child != null; child = child.getNextSibling()) {
            int dpi = searchDPIInNode(child);
            if (dpi > 0) {
                return dpi;
            }
        }

        return 0;
    }

    /**
     * Counts distinct colors in the image, ignoring fully transparent pixels
     */
    private static Map<Integer, Integer> countColors(BufferedImage image) {
        Map<Integer, Integer> colorCounts = new HashMap<>();
        int width = image.getWidth();
        int height = image.getHeight();

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int argb = image.getRGB(x, y);
                int alpha = (argb >> 24) & 0xFF;

                // Ignore fully transparent pixels
                if (alpha == 0) {
                    continue;
                }

                // Store RGB only (ignore alpha for color identity)
                int rgb = argb & 0x00FFFFFF;
                colorCounts.put(rgb, colorCounts.getOrDefault(rgb, 0) + 1);
            }
        }

        return colorCounts;
    }

    /**
     * Sorts colors from lightest to darkest using standard RGB to grayscale conversion
     * Grayscale = 0.299*R + 0.587*G + 0.114*B
     */
    private static List<Integer> sortColorsByLightness(Set<Integer> colors) {
        List<Integer> colorList = new ArrayList<>(colors);

        colorList.sort((c1, c2) -> {
            double lightness1 = calculateLightness(c1);
            double lightness2 = calculateLightness(c2);
            return Double.compare(lightness2, lightness1); // Descending (light to dark)
        });

        // Print sorted colors for debugging
        System.out.println("Colors sorted from lightest to darkest:");
        for (int i = 0; i < colorList.size(); i++) {
            int color = colorList.get(i);
            int r = (color >> 16) & 0xFF;
            int g = (color >> 8) & 0xFF;
            int b = color & 0xFF;
            double lightness = calculateLightness(color);
            System.out.printf("  %d. RGB(%d, %d, %d) - lightness: %.2f%n",
                i + 1, r, g, b, lightness);
        }

        return colorList;
    }

    /**
     * Calculates lightness using standard RGB to grayscale conversion
     */
    private static double calculateLightness(int rgb) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        return 0.299 * r + 0.587 * g + 0.114 * b;
    }


    /**
     * Expands/dilates non-transparent pixels in an image by the specified radius
     * This creates the trap for color registration
     */
    private static BufferedImage expandPixels(BufferedImage source, int expansionRadius) {
        if (expansionRadius <= 0) {
            return source;
        }

        int width = source.getWidth();
        int height = source.getHeight();
        BufferedImage result = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);

        // For each pixel in the result, check if any source pixel within radius is non-transparent
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int sourcePixel = source.getRGB(x, y);
                int sourceAlpha = (sourcePixel >> 24) & 0xFF;

                // If source pixel is already opaque, keep it
                if (sourceAlpha > 0) {
                    result.setRGB(x, y, sourcePixel);
                    continue;
                }

                // Check if any pixel within radius is non-transparent
                int closestColor = 0;
                boolean found = false;

                for (int dy = -expansionRadius; dy <= expansionRadius && !found; dy++) {
                    for (int dx = -expansionRadius; dx <= expansionRadius && !found; dx++) {
                        // Check if within circular radius
                        if (dx * dx + dy * dy <= expansionRadius * expansionRadius) {
                            int sx = x + dx;
                            int sy = y + dy;

                            if (sx >= 0 && sx < width && sy >= 0 && sy < height) {
                                int pixel = source.getRGB(sx, sy);
                                int alpha = (pixel >> 24) & 0xFF;

                                if (alpha > 0) {
                                    closestColor = pixel;
                                    found = true;
                                }
                            }
                        }
                    }
                }

                if (found) {
                    result.setRGB(x, y, closestColor);
                } else {
                    result.setRGB(x, y, 0x00000000); // Transparent
                }
            }
        }

        return result;
    }

    /**
     * Creates color-separated layers with trapping applied
     * Each layer only expands into areas that will be covered by darker colors
     */
    private static List<LayerData> createColorSeparatedLayers(BufferedImage source,
                                                               List<Integer> sortedColors, int dpi,
                                                               double minExpansion, double maxExpansion,
                                                               TrappingStrategy strategy) {
        int width = source.getWidth();
        int height = source.getHeight();

        System.out.println("Building masks for trapping...");
        // Build a "darker colors mask" for each layer
        // This tells us which areas will be covered by darker colors
        List<BufferedImage> darkerColorMasks = new ArrayList<>();
        for (int i = 0; i < sortedColors.size(); i++) {
            System.out.printf("  Creating mask %d/%d...%n", i + 1, sortedColors.size());
            BufferedImage mask = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_BINARY);

            // Mark all pixels that are darker than this layer's color
            int pixelCount = 0;
            int totalPixels = width * height;
            int lastPercent = -1;

            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int pixel = source.getRGB(x, y) & 0x00FFFFFF;

                    // Check if this pixel is darker than current color
                    boolean isDarker = false;
                    for (int j = i + 1; j < sortedColors.size(); j++) {
                        if (pixel == sortedColors.get(j)) {
                            isDarker = true;
                            break;
                        }
                    }

                    if (isDarker) {
                        mask.setRGB(x, y, 0xFFFFFFFF); // White = area covered by darker color
                    } else {
                        mask.setRGB(x, y, 0xFF000000); // Black = area not covered by darker color
                    }

                    pixelCount++;
                    int percent = (pixelCount * 100) / totalPixels;
                    if (percent != lastPercent && percent % 10 == 0) {
                        System.out.printf("    %d%% complete%n", percent);
                        lastPercent = percent;
                    }
                }
            }

            darkerColorMasks.add(mask);
        }
        System.out.println("Masks complete.");

        // Check if first color is white - optimization for white base layer
        boolean firstColorIsWhite = sortedColors.get(0) == 0xFFFFFF;
        if (firstColorIsWhite) {
            System.out.println("First color is white - optimizing by filling entire canvas");
        }

        // Create layers for each color in parallel
        System.out.println("Creating color-separated layers in parallel...");
        int numCores = Runtime.getRuntime().availableProcessors();
        System.out.printf("Using %d CPU cores for parallel processing%n", numCores);

        // Use ExecutorService for better control over parallelism
        ExecutorService executor = Executors.newFixedThreadPool(numCores);
        List<Future<LayerData>> futures = new ArrayList<>();

        for (int i = 0; i < sortedColors.size(); i++) {
            final int layerIndex = i;
            final int targetColor = sortedColors.get(i);
            final BufferedImage mask = darkerColorMasks.get(i);
            final boolean isWhiteBase = (layerIndex == 0 && firstColorIsWhite);

            Future<LayerData> future = executor.submit(() -> {
                int r = (targetColor >> 16) & 0xFF;
                int g = (targetColor >> 8) & 0xFF;
                int b = targetColor & 0xFF;

                System.out.printf("[Layer %d/%d] Processing RGB(%d,%d,%d)%n",
                    layerIndex + 1, sortedColors.size(), r, g, b);

                // Create layer with only this color
                BufferedImage layerImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);

                if (isWhiteBase) {
                    // Optimization: white base layer doesn't need trapping, just copy white pixels
                    System.out.printf("[Layer %d/%d] White base layer (no trapping needed)%n",
                        layerIndex + 1, sortedColors.size());

                    for (int y = 0; y < height; y++) {
                        for (int x = 0; x < width; x++) {
                            int argb = source.getRGB(x, y);
                            int alpha = (argb >> 24) & 0xFF;
                            int rgb = argb & 0x00FFFFFF;

                            // Only include white pixels
                            if (alpha > 0 && rgb == 0xFFFFFF) {
                                layerImage.setRGB(x, y, argb);
                            } else {
                                layerImage.setRGB(x, y, 0x00000000); // Transparent
                            }
                        }
                    }

                    String layerName = String.format("Color_%d_RGB(%d,%d,%d)_notrap",
                        layerIndex + 1, r, g, b);
                    return new LayerData(layerName, layerImage, 0, 0);
                }

                for (int y = 0; y < height; y++) {
                    for (int x = 0; x < width; x++) {
                        int argb = source.getRGB(x, y);
                        int alpha = (argb >> 24) & 0xFF;
                        int rgb = argb & 0x00FFFFFF;

                        // Only include pixels of the target color
                        if (alpha > 0 && rgb == targetColor) {
                            layerImage.setRGB(x, y, argb);
                        } else {
                            layerImage.setRGB(x, y, 0x00000000); // Transparent
                        }
                    }
                }

                // Apply trapping (expansion) - but only into areas with darker colors
                // If first color was white, adjust the expansion calculation
                int adjustedIndex = firstColorIsWhite ? (layerIndex - 1) : layerIndex;
                int adjustedTotal = firstColorIsWhite ? (sortedColors.size() - 1) : sortedColors.size();
                int expansion = strategy.calculateExpansion(adjustedIndex, adjustedTotal, dpi, minExpansion, maxExpansion);
                System.out.printf("[Layer %d/%d] Expansion: %d pixels (%.4f inches)%n",
                    layerIndex + 1, sortedColors.size(), expansion, (double) expansion / dpi);

                BufferedImage expandedLayer;
                if (expansion > 0) {
                    System.out.printf("[Layer %d/%d] Applying trapping...%n", layerIndex + 1, sortedColors.size());
                    expandedLayer = expandPixelsWithMask(layerImage, expansion, mask, layerIndex + 1);
                    System.out.printf("[Layer %d/%d] Trapping complete%n", layerIndex + 1, sortedColors.size());
                } else {
                    expandedLayer = layerImage;
                }

                String layerName = String.format("Color_%d_RGB(%d,%d,%d)_trap%dpx",
                    layerIndex + 1, r, g, b, expansion);
                return new LayerData(layerName, expandedLayer, 0, 0);
            });

            futures.add(future);
        }

        // Collect results in order
        List<LayerData> layers = new ArrayList<>();
        for (Future<LayerData> future : futures) {
            try {
                layers.add(future.get());
            } catch (InterruptedException | ExecutionException e) {
                executor.shutdownNow();
                throw new RuntimeException("Error processing layer in parallel", e);
            }
        }

        executor.shutdown();
        System.out.println("All layers created.");
        return layers;
    }

    /**
     * Expands pixels only into areas marked in the mask (areas that will be covered by darker colors)
     * Uses iterative dilation for better performance
     */
    private static BufferedImage expandPixelsWithMask(BufferedImage source, int expansionRadius, BufferedImage mask, int layerNum) {
        if (expansionRadius <= 0) {
            return source;
        }

        int width = source.getWidth();
        int height = source.getHeight();

        // Start with a copy of the source
        BufferedImage current = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                current.setRGB(x, y, source.getRGB(x, y));
            }
        }

        // Iteratively dilate by 1 pixel at a time for expansionRadius iterations
        // This is much faster than checking a large radius for every pixel
        long startTime = System.currentTimeMillis();

        for (int iteration = 0; iteration < expansionRadius; iteration++) {
            int percent = (iteration * 100) / expansionRadius;
            long elapsed = System.currentTimeMillis() - startTime;
            long remaining = ((expansionRadius - iteration) * elapsed) / (iteration + 1);
            System.out.printf("[Layer %d] Trapping: %d%% (iteration %d/%d, ~%d seconds remaining)%n",
                layerNum, percent, iteration + 1, expansionRadius, remaining / 1000);
            System.out.flush();

            BufferedImage next = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);

            // Copy current to next
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    next.setRGB(x, y, current.getRGB(x, y));
                }
            }

            // Dilate by 1 pixel
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int pixel = current.getRGB(x, y);
                    int alpha = (pixel >> 24) & 0xFF;

                    // Skip if already has a pixel
                    if (alpha > 0) {
                        continue;
                    }

                    // Check if this location is in the mask
                    int maskPixel = mask.getRGB(x, y);
                    boolean inMask = (maskPixel & 0x00FFFFFF) == 0xFFFFFF;

                    if (!inMask) {
                        continue; // Don't expand here
                    }

                    // Check 4-connected neighbors (top, right, bottom, left)
                    int[] dx = {0, 1, 0, -1};
                    int[] dy = {-1, 0, 1, 0};

                    for (int d = 0; d < 4; d++) {
                        int nx = x + dx[d];
                        int ny = y + dy[d];

                        if (nx >= 0 && nx < width && ny >= 0 && ny < height) {
                            int neighborPixel = current.getRGB(nx, ny);
                            int neighborAlpha = (neighborPixel >> 24) & 0xFF;

                            if (neighborAlpha > 0) {
                                next.setRGB(x, y, neighborPixel);
                                break;
                            }
                        }
                    }
                }
            }

            current = next;
        }

        System.out.printf("[Layer %d] Trapping: 100%% complete%n", layerNum);
        System.out.flush();

        return current;
    }

    /**
     * Helper class to hold layer data
     */
    private static class LayerData {
        String name;
        BufferedImage image;
        int left;
        int top;

        LayerData(String name, BufferedImage image, int left, int top) {
            this.name = name;
            this.image = image;
            this.left = left;
            this.top = top;
        }
    }

    /**
     * Creates XMP metadata (Resource 1060) preserving original and adding processing history
     * If original metadata exists, it's preserved as-is and we add a processing history entry
     * If no original metadata, we just add a minimal processing note
     */
    private static byte[] createXMPMetadata(String originalMetadata) {
        // If there's original metadata, preserve it and inject history
        if (originalMetadata != null && !originalMetadata.trim().isEmpty()) {
            String trimmed = originalMetadata.trim();

            // Find the closing </rdf:RDF> tag to inject our history before it
            int rdfEndIndex = trimmed.lastIndexOf("</rdf:RDF>");
            if (rdfEndIndex > 0) {
                StringBuilder result = new StringBuilder();
                result.append(trimmed.substring(0, rdfEndIndex));

                // Add processing history as a separate Description block
                result.append("    <rdf:Description rdf:about=\"\"\n");
                result.append("        xmlns:xmpMM=\"http://ns.adobe.com/xap/1.0/mm/\"\n");
                result.append("        xmlns:stEvt=\"http://ns.adobe.com/xap/1.0/sType/ResourceEvent#\">\n");
                result.append("      <xmpMM:History>\n");
                result.append("        <rdf:Seq>\n");
                result.append("          <rdf:li rdf:parseType=\"Resource\">\n");
                result.append("            <stEvt:action>trapped</stEvt:action>\n");
                result.append("            <stEvt:softwareAgent>Trapper v2.0 (https://github.com/electrosaur-labs/trapper)</stEvt:softwareAgent>\n");
                result.append("            <stEvt:changed>/</stEvt:changed>\n");
                result.append("          </rdf:li>\n");
                result.append("        </rdf:Seq>\n");
                result.append("      </xmpMM:History>\n");
                result.append("    </rdf:Description>\n");

                result.append(trimmed.substring(rdfEndIndex));

                try {
                    return result.toString().getBytes("UTF-8");
                } catch (Exception e) {
                    return result.toString().getBytes();
                }
            }
        }

        // No original metadata - create minimal XMP with just processing note
        StringBuilder xmp = new StringBuilder();
        xmp.append("<?xpacket begin=\"\ufeff\" id=\"W5M0MpCehiHzreSzNTczkc9d\"?>\n");
        xmp.append("<x:xmpmeta xmlns:x=\"adobe:ns:meta/\" x:xmptk=\"Trapper v2.0\">\n");
        xmp.append("  <rdf:RDF xmlns:rdf=\"http://www.w3.org/1999/02/22-rdf-syntax-ns#\">\n");
        xmp.append("    <rdf:Description rdf:about=\"\"\n");
        xmp.append("        xmlns:xmpMM=\"http://ns.adobe.com/xap/1.0/mm/\"\n");
        xmp.append("        xmlns:stEvt=\"http://ns.adobe.com/xap/1.0/sType/ResourceEvent#\">\n");
        xmp.append("      <xmpMM:History>\n");
        xmp.append("        <rdf:Seq>\n");
        xmp.append("          <rdf:li rdf:parseType=\"Resource\">\n");
        xmp.append("            <stEvt:action>trapped</stEvt:action>\n");
        xmp.append("            <stEvt:softwareAgent>Trapper v2.0 (https://github.com/electrosaur-labs/trapper)</stEvt:softwareAgent>\n");
        xmp.append("            <stEvt:changed>/</stEvt:changed>\n");
        xmp.append("          </rdf:li>\n");
        xmp.append("        </rdf:Seq>\n");
        xmp.append("      </xmpMM:History>\n");
        xmp.append("    </rdf:Description>\n");
        xmp.append("  </rdf:RDF>\n");
        xmp.append("</x:xmpmeta>\n");

        // Padding to make deterministic size (helps with test reproducibility)
        int paddingNeeded = Math.max(0, 1024 - xmp.length() - 100);
        for (int i = 0; i < paddingNeeded; i++) {
            xmp.append(" ");
        }

        xmp.append("<?xpacket end=\"w\"?>");

        try {
            return xmp.toString().getBytes("UTF-8");
        } catch (Exception e) {
            return xmp.toString().getBytes();
        }
    }

    /**
     * Escapes XML special characters for XMP metadata
     */
    private static String escapeXML(String text) {
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;")
                   .replace("'", "&apos;")
                   .replace("\n", "&#xA;");
    }

    /**
     * Writes an image resource block to PSD
     * Resource ID 1034 is for Copyright/Creator metadata
     */
    private static void writeImageResourceBlock(DataOutputStream dos, int resourceId, byte[] data) throws IOException {
        dos.writeBytes("8BIM");           // Signature
        dos.writeShort(resourceId);       // Resource ID (1034 = Copyright)
        dos.writeShort(0);                // Name (Pascal string, empty)

        // Data size (must be even)
        int dataSize = data.length;
        if (dataSize % 2 != 0) {
            dataSize++;  // Pad to even
        }
        dos.writeInt(dataSize);
        dos.write(data);

        // Add padding byte if needed
        if (data.length % 2 != 0) {
            dos.writeByte(0);
        }
    }

    /**
     * Writes a multi-layer PSD file with metadata
     */
    private static void writePSD(String filename, int width, int height,
                                  List<LayerData> layers, String originalMetadata) throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(filename, "rw");
             FileChannel channel = raf.getChannel()) {

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(baos);

            // File Header (26 bytes)
            dos.writeBytes("8BPS");           // Signature
            dos.writeShort(1);                // Version
            dos.write(new byte[6]);           // Reserved
            dos.writeShort(3);                // Channels (RGB)
            dos.writeInt(height);             // Height
            dos.writeInt(width);              // Width
            dos.writeShort(8);                // Bits per channel
            dos.writeShort(3);                // Color mode (RGB)

            // Color Mode Data Section
            dos.writeInt(0);                  // No color mode data

            // Image Resources Section
            ByteArrayOutputStream resourcesStream = new ByteArrayOutputStream();
            DataOutputStream resources = new DataOutputStream(resourcesStream);

            // Add XMP metadata (Resource 1060 - what Photoshop File Info reads)
            // Preserves original metadata if present, appends Trapper processing info
            writeImageResourceBlock(resources, 1060, createXMPMetadata(originalMetadata));

            byte[] resourcesBytes = resourcesStream.toByteArray();
            dos.writeInt(resourcesBytes.length);
            dos.write(resourcesBytes);

            // Layer and Mask Information Section
            ByteArrayOutputStream layerInfoStream = new ByteArrayOutputStream();
            DataOutputStream layerInfo = new DataOutputStream(layerInfoStream);

            // Layer info
            layerInfo.writeShort(layers.size());  // Number of layers

            // Write layer records
            for (LayerData layer : layers) {
                writeLayerRecord(layerInfo, layer);
            }

            // Write layer image data
            for (LayerData layer : layers) {
                writeLayerImageData(layerInfo, layer.image);
            }

            byte[] layerInfoBytes = layerInfoStream.toByteArray();
            dos.writeInt(layerInfoBytes.length + 4);  // Layer and mask section length
            dos.writeInt(layerInfoBytes.length);      // Layer info length
            dos.write(layerInfoBytes);

            // Image Data Section (merged/flattened composite image)
            // Use a neutral gray background so all colors including white are visible
            BufferedImage composite = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            Graphics2D gc = composite.createGraphics();
            gc.setColor(new Color(128, 128, 128)); // Medium gray background
            gc.fillRect(0, 0, width, height);

            // Composite all layers from lightest to darkest
            for (LayerData layer : layers) {
                gc.drawImage(layer.image, layer.left, layer.top, null);
            }
            gc.dispose();

            dos.writeShort(1);  // Compression: RLE
            writeImageData(dos, composite);

            // Write to file
            channel.write(ByteBuffer.wrap(baos.toByteArray()));
        }
    }

    /**
     * Pre-computes RLE compressed size for a channel
     */
    private static int calculateRLEChannelSize(BufferedImage img, int channelId) throws IOException {
        int width = img.getWidth();
        int height = img.getHeight();

        int totalSize = 2 + (height * 2); // compression flag + byte counts

        for (int y = 0; y < height; y++) {
            byte[] rowData = new byte[width];
            for (int x = 0; x < width; x++) {
                int pixel = img.getRGB(x, y);
                int value;
                if (channelId == -1) {
                    value = (pixel >> 24) & 0xFF;  // Alpha
                } else if (channelId == 0) {
                    value = (pixel >> 16) & 0xFF;  // Red
                } else if (channelId == 1) {
                    value = (pixel >> 8) & 0xFF;   // Green
                } else {
                    value = pixel & 0xFF;           // Blue
                }
                rowData[x] = (byte)value;
            }
            totalSize += compressRLE(rowData).length;
        }

        return totalSize;
    }

    private static void writeLayerRecord(DataOutputStream dos, LayerData layer) throws IOException {
        int width = layer.image.getWidth();
        int height = layer.image.getHeight();

        dos.writeInt(layer.top);                    // Top
        dos.writeInt(layer.left);                   // Left
        dos.writeInt(layer.top + height);           // Bottom
        dos.writeInt(layer.left + width);           // Right

        dos.writeShort(4);                          // Number of channels (RGBA)

        // Channel info - calculate actual compressed sizes
        dos.writeShort(-1);                         // Channel ID -1 = alpha
        dos.writeInt(calculateRLEChannelSize(layer.image, -1));

        // RGB channels
        for (int i = 0; i < 3; i++) {
            dos.writeShort(i);                      // Channel ID (0=R, 1=G, 2=B)
            dos.writeInt(calculateRLEChannelSize(layer.image, i));
        }

        dos.writeBytes("8BIM");                     // Blend mode signature
        dos.writeBytes("norm");                     // Blend mode key (normal)
        dos.writeByte(255);                         // Opacity
        dos.writeByte(0);                           // Clipping
        dos.writeByte(0);                           // Flags
        dos.writeByte(0);                           // Filler

        // Extra data
        ByteArrayOutputStream extraData = new ByteArrayOutputStream();
        DataOutputStream extra = new DataOutputStream(extraData);

        // Layer mask data
        extra.writeInt(0);

        // Layer blending ranges
        extra.writeInt(0);

        // Layer name (Pascal string)
        byte[] nameBytes = layer.name.getBytes("ISO-8859-1");
        extra.writeByte(Math.min(nameBytes.length, 255));
        extra.write(nameBytes);
        // Pad to multiple of 4
        int padding = (4 - ((nameBytes.length + 1) % 4)) % 4;
        extra.write(new byte[padding]);

        byte[] extraBytes = extraData.toByteArray();
        dos.writeInt(extraBytes.length);
        dos.write(extraBytes);
    }

    /**
     * PackBits RLE compression for a single scanline
     * Returns compressed data for one row
     */
    private static byte[] compressRLE(byte[] data) {
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        int i = 0;

        while (i < data.length) {
            // Look for runs of identical bytes
            int runLength = 1;
            while (i + runLength < data.length &&
                   data[i] == data[i + runLength] &&
                   runLength < 128) {
                runLength++;
            }

            if (runLength >= 2) {
                // RLE: write (1 - runLength) followed by the byte
                result.write((byte)(1 - runLength));
                result.write(data[i]);
                i += runLength;
            } else {
                // Literal run: find how many non-repeating bytes
                int literalLength = 1;
                while (i + literalLength < data.length && literalLength < 128) {
                    // Check if next bytes are a run
                    int nextRun = 1;
                    if (i + literalLength + 1 < data.length) {
                        while (i + literalLength + nextRun < data.length &&
                               data[i + literalLength] == data[i + literalLength + nextRun] &&
                               nextRun < 3) {
                            nextRun++;
                        }
                    }

                    // If we found a run of 3+, stop the literal run
                    if (nextRun >= 3) {
                        break;
                    }

                    literalLength++;
                }

                // Write literal run: length-1 followed by the bytes
                result.write((byte)(literalLength - 1));
                result.write(data, i, literalLength);
                i += literalLength;
            }
        }

        return result.toByteArray();
    }

    private static void writeLayerImageData(DataOutputStream dos, BufferedImage img) throws IOException {
        int width = img.getWidth();
        int height = img.getHeight();

        // Write alpha channel first (channel ID -1)
        dos.writeShort(1);  // RLE compression

        byte[][] compressedRows = new byte[height][];
        for (int y = 0; y < height; y++) {
            byte[] rowData = new byte[width];
            for (int x = 0; x < width; x++) {
                int argb = img.getRGB(x, y);
                rowData[x] = (byte)((argb >> 24) & 0xFF);
            }
            compressedRows[y] = compressRLE(rowData);
        }

        // Write byte counts
        for (int y = 0; y < height; y++) {
            dos.writeShort(compressedRows[y].length);
        }
        // Write compressed data
        for (int y = 0; y < height; y++) {
            dos.write(compressedRows[y]);
        }

        // Write each RGB channel
        for (int channel = 0; channel < 3; channel++) {
            dos.writeShort(1);  // RLE compression

            compressedRows = new byte[height][];
            for (int y = 0; y < height; y++) {
                byte[] rowData = new byte[width];
                for (int x = 0; x < width; x++) {
                    int rgb = img.getRGB(x, y);
                    int value;
                    if (channel == 0) value = (rgb >> 16) & 0xFF;      // Red
                    else if (channel == 1) value = (rgb >> 8) & 0xFF;  // Green
                    else value = rgb & 0xFF;                           // Blue
                    rowData[x] = (byte)value;
                }
                compressedRows[y] = compressRLE(rowData);
            }

            // Write byte counts
            for (int y = 0; y < height; y++) {
                dos.writeShort(compressedRows[y].length);
            }
            // Write compressed data
            for (int y = 0; y < height; y++) {
                dos.write(compressedRows[y]);
            }
        }
    }

    private static void writeImageData(DataOutputStream dos, BufferedImage img) throws IOException {
        int width = img.getWidth();
        int height = img.getHeight();

        // Write byte counts for all channels first (for RLE compression)
        List<byte[][]> allCompressedRows = new ArrayList<>();

        // Compress all channels
        for (int channel = 0; channel < 3; channel++) {
            byte[][] compressedRows = new byte[height][];
            for (int y = 0; y < height; y++) {
                byte[] rowData = new byte[width];
                for (int x = 0; x < width; x++) {
                    int rgb = img.getRGB(x, y);
                    int value;
                    if (channel == 0) value = (rgb >> 16) & 0xFF;      // Red
                    else if (channel == 1) value = (rgb >> 8) & 0xFF;  // Green
                    else value = rgb & 0xFF;                           // Blue
                    rowData[x] = (byte)value;
                }
                compressedRows[y] = compressRLE(rowData);
            }
            allCompressedRows.add(compressedRows);
        }

        // Write byte counts for all scanlines of all channels
        for (int channel = 0; channel < 3; channel++) {
            byte[][] compressedRows = allCompressedRows.get(channel);
            for (int y = 0; y < height; y++) {
                dos.writeShort(compressedRows[y].length);
            }
        }

        // Write compressed data for all channels
        for (int channel = 0; channel < 3; channel++) {
            byte[][] compressedRows = allCompressedRows.get(channel);
            for (int y = 0; y < height; y++) {
                dos.write(compressedRows[y]);
            }
        }
    }
}
