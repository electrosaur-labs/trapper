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
            System.err.println("Usage: java PsdColorSeparator <input.psd> [minTrap] [maxTrap]");
            System.err.println("  minTrap: minimum trap size (darkest layer, default: 0)");
            System.err.println("  maxTrap: maximum trap size (lightest layer, default: 1/32)");
            System.err.println("  Trap sizes can be specified as:");
            System.err.println("    - Fractions: 1/32, 1/64, 1/16, etc.");
            System.err.println("    - Decimals: 0.03125, 0.015625, etc.");
            System.exit(1);
        }

        String inputFile = args[0];
        String outputFile = generateOutputFilename(inputFile);

        // Parse optional trap size arguments
        double minExpansion = DEFAULT_MIN_EXPANSION;
        double maxExpansion = DEFAULT_MAX_EXPANSION;

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
            processFile(inputFile, outputFile, minExpansion, maxExpansion);
            System.out.println("Successfully created color-separated PSD: " + outputFile);
        } catch (Exception e) {
            System.err.println("Error processing PSD file: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    /**
     * Parses a trap size specification in either fractional (1/32) or decimal (0.03125) format
     */
    private static double parseTrapSize(String spec) {
        spec = spec.trim();

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
                throw new IllegalArgumentException("Invalid decimal format: " + spec + ". Use format like 0.03125 or 1/32");
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
                                   double minExpansion, double maxExpansion) throws IOException {
        // Step 1: Read invisible layers from input PSD
        System.out.println("Reading invisible layers from input...");
        List<LayerData> invisibleLayers = readInvisibleLayers(inputFile);

        // Step 2: Read and flatten the PSD, get DPI
        PsdInfo psdInfo = readAndFlattenPSD(inputFile);
        BufferedImage flattened = psdInfo.image;
        int dpi = psdInfo.dpi;

        System.out.println("Image DPI: " + dpi);
        System.out.printf("Trap range: %.6f\" to %.6f\" (1/%d\" to 1/%d\")%n",
            minExpansion, maxExpansion,
            minExpansion > 0 ? (int)Math.round(1.0 / minExpansion) : 0,
            maxExpansion > 0 ? (int)Math.round(1.0 / maxExpansion) : 0);

        // Step 3: Count distinct colors (ignoring transparent pixels)
        Map<Integer, Integer> colorCounts = countColors(flattened);

        // Step 4: Check color count
        if (colorCounts.size() > MAX_COLORS) {
            throw new IllegalStateException(
                String.format("Image has %d distinct colors, exceeds maximum of %d",
                    colorCounts.size(), MAX_COLORS));
        }

        System.out.println("Found " + colorCounts.size() + " distinct colors");

        // Step 5: Sort colors by lightness (light to dark)
        List<Integer> sortedColors = sortColorsByLightness(colorCounts.keySet());

        // Step 6: Create output PSD with color-separated layers and trapping
        List<LayerData> colorSeparatedLayers = createColorSeparatedLayers(flattened, sortedColors, dpi,
                                                                          minExpansion, maxExpansion);

        // Step 7: Combine invisible layers (on top) with color-separated layers (below)
        List<LayerData> allLayers = new ArrayList<>();
        allLayers.addAll(invisibleLayers); // Invisible layers first (appear on top in Photoshop)
        allLayers.addAll(colorSeparatedLayers); // Color-separated layers below

        System.out.println("Total layers in output: " + allLayers.size() +
                         " (" + invisibleLayers.size() + " invisible + " +
                         colorSeparatedLayers.size() + " color-separated)");

        // Step 8: Verify that flattening the trapped layers matches the original
        // Note: We only verify color-separated layers, not invisible ones
        System.out.println("Verifying trapped output...");
        int width = flattened.getWidth();
        int height = flattened.getHeight();
        BufferedImage trappedFlattened = flattenLayers(colorSeparatedLayers, width, height);
        verifyFlattening(flattened, trappedFlattened);

        // Step 9: Write the PSD file
        System.out.println("Writing output PSD file...");
        writePSD(outputFile, width, height, allLayers);
        System.out.println("Output file written.");
    }

    /**
     * Flattens layers by compositing them from lightest to darkest
     */
    private static BufferedImage flattenLayers(List<LayerData> layers, int width, int height) {
        BufferedImage result = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = result.createGraphics();

        // Start with transparent/black background
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, width, height);

        // Composite layers from lightest to darkest (order they're in the list)
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
     */
    private static void verifyFlattening(BufferedImage original, BufferedImage flattened) {
        int width = original.getWidth();
        int height = original.getHeight();
        int differences = 0;
        Map<String, Integer> missingColors = new HashMap<>();

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int origRgb = original.getRGB(x, y) & 0x00FFFFFF;
                int flatRgb = flattened.getRGB(x, y) & 0x00FFFFFF;

                if (origRgb != flatRgb) {
                    differences++;
                    int r = (origRgb >> 16) & 0xFF;
                    int g = (origRgb >> 8) & 0xFF;
                    int b = origRgb & 0xFF;
                    String colorKey = String.format("RGB(%d,%d,%d)", r, g, b);
                    missingColors.put(colorKey, missingColors.getOrDefault(colorKey, 0) + 1);
                }
            }
        }

        if (differences > 0) {
            System.err.println("ERROR: Flattened trapped image differs from original!");
            System.err.println("Number of different pixels: " + differences + " out of " + (width * height));
            System.err.println("Colors with missing/wrong pixels:");
            for (Map.Entry<String, Integer> entry : missingColors.entrySet()) {
                System.err.println("  " + entry.getKey() + ": " + entry.getValue() + " pixels");
            }
        } else {
            System.out.println("Verification passed: Flattened trapped image matches original");
        }
    }

    /**
     * Helper class to hold PSD info
     */
    private static class PsdInfo {
        BufferedImage image;
        int dpi;

        PsdInfo(BufferedImage image, int dpi) {
            this.image = image;
            this.dpi = dpi;
        }
    }

    /**
     * Reads a PSD file and flattens all layers into a single image
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

            // Read the composite/flattened image (index -1 or 0)
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

            reader.dispose();

            System.out.println("Read PSD: " + image.getWidth() + "x" + image.getHeight());
            return new PsdInfo(image, dpi);
        }
    }

    /**
     * Reads individual layers from a PSD file, including invisible layers
     * Returns a list of invisible layers only
     */
    private static List<LayerData> readInvisibleLayers(String filename) throws IOException {
        List<LayerData> invisibleLayers = new ArrayList<>();

        try (RandomAccessFile raf = new RandomAccessFile(filename, "r")) {
            // Skip file header (26 bytes)
            raf.skipBytes(26);

            // Skip color mode data section
            int colorModeDataLength = raf.readInt();
            raf.skipBytes(colorModeDataLength);

            // Skip image resources section
            int imageResourcesLength = raf.readInt();
            raf.skipBytes(imageResourcesLength);

            // Read layer and mask information section
            int layerMaskInfoLength = raf.readInt();
            if (layerMaskInfoLength == 0) {
                return invisibleLayers; // No layers in this file
            }

            long layerMaskInfoEnd = raf.getFilePointer() + layerMaskInfoLength;

            // Read layer info section
            int layerInfoLength = raf.readInt();
            if (layerInfoLength == 0) {
                return invisibleLayers;
            }

            // Read number of layers
            short layerCount = raf.readShort();
            int absoluteLayerCount = Math.abs(layerCount);

            System.out.println("Found " + absoluteLayerCount + " layers in input PSD");

            // Read layer records
            List<LayerRecord> layerRecords = new ArrayList<>();
            for (int i = 0; i < absoluteLayerCount; i++) {
                LayerRecord record = readLayerRecord(raf);
                layerRecords.add(record);

                if (!record.visible) {
                    System.out.println("  Layer " + (i + 1) + ": \"" + record.name + "\" (invisible)");
                }
            }

            // Read layer image data
            for (int i = 0; i < absoluteLayerCount; i++) {
                LayerRecord record = layerRecords.get(i);

                // Only read image data for invisible layers
                if (!record.visible) {
                    BufferedImage layerImage = readLayerImage(raf, record);
                    invisibleLayers.add(new LayerData(
                        record.name,
                        layerImage,
                        record.left,
                        record.top,
                        false // invisible
                    ));
                } else {
                    // Skip visible layer image data
                    skipLayerImageData(raf, record);
                }
            }
        }

        System.out.println("Read " + invisibleLayers.size() + " invisible layers from input");
        return invisibleLayers;
    }

    /**
     * Helper class to hold layer record information
     */
    private static class LayerRecord {
        int top, left, bottom, right;
        int width, height;
        int channelCount;
        List<ChannelInfo> channels;
        String name;
        boolean visible;

        static class ChannelInfo {
            short id;
            int dataLength;
        }
    }

    /**
     * Reads a layer record from the PSD file
     */
    private static LayerRecord readLayerRecord(RandomAccessFile raf) throws IOException {
        LayerRecord record = new LayerRecord();

        // Read rectangle
        record.top = raf.readInt();
        record.left = raf.readInt();
        record.bottom = raf.readInt();
        record.right = raf.readInt();

        record.width = record.right - record.left;
        record.height = record.bottom - record.top;

        // Read number of channels
        record.channelCount = raf.readUnsignedShort();
        record.channels = new ArrayList<>();

        // Read channel information
        for (int i = 0; i < record.channelCount; i++) {
            LayerRecord.ChannelInfo channel = new LayerRecord.ChannelInfo();
            channel.id = raf.readShort();
            channel.dataLength = raf.readInt();
            record.channels.add(channel);
        }

        // Read blend mode signature (should be '8BIM')
        byte[] sig = new byte[4];
        raf.readFully(sig);

        // Read blend mode key
        raf.skipBytes(4);

        // Read opacity
        raf.skipBytes(1);

        // Read clipping
        raf.skipBytes(1);

        // Read flags byte - THIS IS WHERE VISIBILITY IS STORED
        int flags = raf.readUnsignedByte();
        record.visible = (flags & 0x02) != 0; // bit 1 = visible

        // Read filler
        raf.skipBytes(1);

        // Read extra data length
        int extraDataLength = raf.readInt();
        long extraDataEnd = raf.getFilePointer() + extraDataLength;

        // Skip layer mask data
        int layerMaskDataLength = raf.readInt();
        raf.skipBytes(layerMaskDataLength);

        // Skip layer blending ranges
        int blendingRangesLength = raf.readInt();
        raf.skipBytes(blendingRangesLength);

        // Read layer name (Pascal string)
        int nameLength = raf.readUnsignedByte();
        byte[] nameBytes = new byte[nameLength];
        raf.readFully(nameBytes);
        record.name = new String(nameBytes, "ISO-8859-1");

        // Skip to end of extra data (includes padding)
        raf.seek(extraDataEnd);

        return record;
    }

    /**
     * Reads layer image data for a specific layer
     */
    private static BufferedImage readLayerImage(RandomAccessFile raf, LayerRecord record) throws IOException {
        int width = record.width;
        int height = record.height;

        if (width <= 0 || height <= 0) {
            // Empty layer, return transparent image
            return new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        }

        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);

        // Read each channel
        byte[][] channelData = new byte[record.channelCount][width * height];

        for (int c = 0; c < record.channelCount; c++) {
            // Read compression method
            int compression = raf.readUnsignedShort();

            if (compression == 0) {
                // Raw data
                raf.readFully(channelData[c]);
            } else if (compression == 1) {
                // RLE compressed
                channelData[c] = readRLEChannel(raf, width, height);
            } else {
                throw new IOException("Unsupported compression method: " + compression);
            }
        }

        // Map channel data to ARGB image
        // Channel IDs: -1 = alpha, 0 = red, 1 = green, 2 = blue
        int alphaIndex = -1;
        int redIndex = -1;
        int greenIndex = -1;
        int blueIndex = -1;

        for (int c = 0; c < record.channelCount; c++) {
            short channelId = record.channels.get(c).id;
            if (channelId == -1) alphaIndex = c;
            else if (channelId == 0) redIndex = c;
            else if (channelId == 1) greenIndex = c;
            else if (channelId == 2) blueIndex = c;
        }

        // Build ARGB image
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int idx = y * width + x;

                int a = (alphaIndex >= 0) ? (channelData[alphaIndex][idx] & 0xFF) : 255;
                int r = (redIndex >= 0) ? (channelData[redIndex][idx] & 0xFF) : 0;
                int g = (greenIndex >= 0) ? (channelData[greenIndex][idx] & 0xFF) : 0;
                int b = (blueIndex >= 0) ? (channelData[blueIndex][idx] & 0xFF) : 0;

                int argb = (a << 24) | (r << 16) | (g << 8) | b;
                image.setRGB(x, y, argb);
            }
        }

        return image;
    }

    /**
     * Reads an RLE-compressed channel
     */
    private static byte[] readRLEChannel(RandomAccessFile raf, int width, int height) throws IOException {
        byte[] result = new byte[width * height];

        // Read byte counts for each scanline
        int[] byteCounts = new int[height];
        for (int i = 0; i < height; i++) {
            byteCounts[i] = raf.readUnsignedShort();
        }

        // Read and decompress each scanline
        int destIdx = 0;
        for (int row = 0; row < height; row++) {
            byte[] compressedRow = new byte[byteCounts[row]];
            raf.readFully(compressedRow);

            // Decompress PackBits RLE
            int srcIdx = 0;
            int rowDestIdx = 0;
            while (srcIdx < compressedRow.length && rowDestIdx < width) {
                byte header = compressedRow[srcIdx++];

                if (header >= 0) {
                    // Literal run: copy (header + 1) bytes
                    int count = header + 1;
                    for (int i = 0; i < count && rowDestIdx < width; i++) {
                        result[destIdx++] = compressedRow[srcIdx++];
                        rowDestIdx++;
                    }
                } else if (header != -128) {
                    // RLE run: repeat next byte (1 - header) times
                    int count = 1 - header;
                    byte value = compressedRow[srcIdx++];
                    for (int i = 0; i < count && rowDestIdx < width; i++) {
                        result[destIdx++] = value;
                        rowDestIdx++;
                    }
                }
                // header == -128 is a no-op
            }
        }

        return result;
    }

    /**
     * Skips layer image data without reading it into memory
     */
    private static void skipLayerImageData(RandomAccessFile raf, LayerRecord record) throws IOException {
        for (int c = 0; c < record.channelCount; c++) {
            int dataLength = record.channels.get(c).dataLength;
            raf.skipBytes(dataLength);
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
                        return Integer.parseInt(value.replaceAll("[^0-9]", ""));
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
     * Calculates the expansion (trap size) in pixels for a given layer index
     * Lightest layer (index 0): maxExpansion
     * Darkest layer (index n-1): minExpansion
     * Linear interpolation for layers in between
     */
    private static int calculateExpansion(int layerIndex, int totalLayers, int dpi,
                                         double minExpansion, double maxExpansion) {
        if (totalLayers == 1) {
            // Single color: use minimum expansion
            return (int) Math.round(minExpansion * dpi);
        }

        // Linear interpolation from lightest to darkest
        double ratio = (double) layerIndex / (totalLayers - 1);
        double expansionInches = maxExpansion - (ratio * (maxExpansion - minExpansion));

        // Convert to pixels
        int expansionPixels = (int) Math.round(expansionInches * dpi);

        return expansionPixels;
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
                                                               double minExpansion, double maxExpansion) {
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
                int expansion = calculateExpansion(adjustedIndex, adjustedTotal, dpi, minExpansion, maxExpansion);
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
        boolean visible;

        LayerData(String name, BufferedImage image, int left, int top) {
            this(name, image, left, top, true);
        }

        LayerData(String name, BufferedImage image, int left, int top, boolean visible) {
            this.name = name;
            this.image = image;
            this.left = left;
            this.top = top;
            this.visible = visible;
        }
    }

    /**
     * Writes a multi-layer PSD file
     */
    private static void writePSD(String filename, int width, int height,
                                  List<LayerData> layers) throws IOException {
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
            dos.writeInt(0);                  // No image resources

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

        // Flags byte: bit 1 = visible (0x02)
        int flags = layer.visible ? 0x02 : 0x00;
        dos.writeByte(flags);                       // Flags
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
