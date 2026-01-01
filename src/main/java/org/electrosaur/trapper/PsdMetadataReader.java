package org.electrosaur.trapper;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.stream.ImageInputStream;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Reads and displays relevant metadata from PSD files:
 * - Dimensions (width x height)
 * - Resolution (DPI)
 * - Number of layers
 * - Number of distinct colors
 * - File size
 */
public class PsdMetadataReader {

    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: gradle psdInfo -PpsdFile=<file.psd>");
            System.exit(1);
        }

        String filename = args[0];
        File file = new File(filename);

        if (!file.exists()) {
            System.err.println("Error: File not found: " + filename);
            System.exit(1);
        }

        try {
            PsdMetadata metadata = readPsdMetadata(file);
            printMetadata(filename, metadata);
        } catch (Exception e) {
            System.err.println("Error reading PSD file: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    /**
     * Container for PSD metadata
     */
    private static class PsdMetadata {
        int width;
        int height;
        int dpi;
        int layers;
        int distinctColors;
        long fileSize;
    }

    /**
     * Reads metadata from a PSD file
     */
    private static PsdMetadata readPsdMetadata(File file) throws Exception {
        PsdMetadata metadata = new PsdMetadata();
        metadata.fileSize = file.length();

        try (ImageInputStream iis = ImageIO.createImageInputStream(file)) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);

            if (!readers.hasNext()) {
                throw new Exception("No ImageReader found for PSD file");
            }

            ImageReader reader = readers.next();
            reader.setInput(iis);

            // Get dimensions
            metadata.width = reader.getWidth(0);
            metadata.height = reader.getHeight(0);

            // Get DPI
            metadata.dpi = extractDPI(reader);

            // Count layers
            metadata.layers = countLayers(reader);

            // Read flattened image and count colors
            BufferedImage image = reader.read(0);
            metadata.distinctColors = countDistinctColors(image);

            reader.dispose();
        }

        return metadata;
    }

    /**
     * Extract DPI from ImageReader metadata
     */
    private static int extractDPI(ImageReader reader) {
        try {
            IIOMetadata metadata = reader.getImageMetadata(0);
            if (metadata != null) {
                String[] formatNames = metadata.getMetadataFormatNames();
                for (String formatName : formatNames) {
                    Node root = metadata.getAsTree(formatName);
                    int dpi = searchDPIInNode(root);
                    if (dpi > 0) {
                        return dpi;
                    }
                }
            }
        } catch (Exception e) {
            // Metadata reading failed
        }
        return 72; // Default
    }

    /**
     * Recursively search for DPI information in metadata nodes
     */
    private static int searchDPIInNode(Node node) {
        // Look for HorizontalPixelSize (in mm)
        if (node.getNodeName().equals("HorizontalPixelSize")) {
            NamedNodeMap attributes = node.getAttributes();
            if (attributes != null) {
                Node valueNode = attributes.getNamedItem("value");
                if (valueNode != null) {
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
     * Count number of layers in PSD
     */
    private static int countLayers(ImageReader reader) {
        try {
            int numImages = reader.getNumImages(true);
            if (numImages > 1) {
                return numImages;
            }
        } catch (Exception e) {
            // Layer counting failed
        }
        return 1; // At least the composite/flattened image
    }

    /**
     * Count distinct colors in an image (excluding fully transparent pixels)
     */
    private static int countDistinctColors(BufferedImage image) {
        Map<Integer, Boolean> colors = new HashMap<>();
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
                colors.put(rgb, true);
            }
        }

        return colors.size();
    }

    /**
     * Print metadata in a readable format
     */
    private static void printMetadata(String filename, PsdMetadata metadata) {
        System.out.println("PSD File Information");
        System.out.println("===================");
        System.out.println();
        System.out.println("File:             " + filename);
        System.out.println("File Size:        " + formatFileSize(metadata.fileSize));
        System.out.println();
        System.out.println("Dimensions:       " + metadata.width + " × " + metadata.height + " pixels");
        System.out.println("Resolution:       " + metadata.dpi + " DPI");
        System.out.println("Physical Size:    " +
            String.format("%.2f", (double) metadata.width / metadata.dpi) + "\" × " +
            String.format("%.2f", (double) metadata.height / metadata.dpi) + "\"");
        System.out.println();
        System.out.println("Layers:           " + metadata.layers +
            (metadata.layers == 1 ? " (flattened)" : ""));
        System.out.println("Distinct Colors:  " + metadata.distinctColors);
        System.out.println();

        // Additional info
        long pixelCount = (long) metadata.width * metadata.height;
        System.out.println("Total Pixels:     " + formatNumber(pixelCount));

        // Estimate uncompressed size
        long uncompressedSize = pixelCount * 4; // RGBA = 4 bytes per pixel
        System.out.println("Uncompressed:     " + formatFileSize(uncompressedSize) + " (RGBA)");

        // Compression ratio
        double compressionRatio = (double) uncompressedSize / metadata.fileSize;
        System.out.println("Compression:      " + String.format("%.1fx", compressionRatio) +
            " (" + String.format("%.1f%%", (1 - 1.0/compressionRatio) * 100) + " reduction)");
    }

    /**
     * Format file size in human-readable format
     */
    private static String formatFileSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format("%.2f KB", bytes / 1024.0);
        } else if (bytes < 1024 * 1024 * 1024) {
            return String.format("%.2f MB", bytes / (1024.0 * 1024.0));
        } else {
            return String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0));
        }
    }

    /**
     * Format large numbers with commas
     */
    private static String formatNumber(long number) {
        return String.format("%,d", number);
    }
}
