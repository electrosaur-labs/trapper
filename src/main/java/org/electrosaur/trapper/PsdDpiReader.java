package org.electrosaur.trapper;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.stream.ImageInputStream;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import java.io.File;
import java.util.Iterator;

/**
 * Simple program to read and display DPI information from PSD files
 */
public class PsdDpiReader {

    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java PsdDpiReader <file.psd>");
            System.exit(1);
        }

        String filename = args[0];
        File file = new File(filename);

        if (!file.exists()) {
            System.err.println("File not found: " + filename);
            System.exit(1);
        }

        System.out.println("Reading PSD file: " + filename);
        System.out.println("File size: " + file.length() + " bytes");
        System.out.println();

        try (ImageInputStream iis = ImageIO.createImageInputStream(file)) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);

            if (!readers.hasNext()) {
                System.err.println("No ImageReader found for this file");
                System.exit(1);
            }

            ImageReader reader = readers.next();
            System.out.println("ImageReader: " + reader.getClass().getName());
            reader.setInput(iis);

            // Get dimensions
            int width = reader.getWidth(0);
            int height = reader.getHeight(0);
            System.out.println("Dimensions: " + width + " x " + height + " pixels");
            System.out.println();

            // Get metadata
            IIOMetadata metadata = reader.getImageMetadata(0);
            if (metadata == null) {
                System.out.println("No metadata available");
                System.exit(0);
            }

            // Print all metadata format names
            String[] formatNames = metadata.getMetadataFormatNames();
            System.out.println("Available metadata formats:");
            for (String format : formatNames) {
                System.out.println("  - " + format);
            }
            System.out.println();

            // Print metadata tree for each format
            for (String formatName : formatNames) {
                System.out.println("=== Metadata format: " + formatName + " ===");
                Node root = metadata.getAsTree(formatName);
                printNode(root, 0);
                System.out.println();
            }

            // Try to extract DPI using the current method
            System.out.println("=== DPI Extraction ===");
            int dpi = extractDPI(metadata);
            System.out.println("Extracted DPI: " + dpi);

            reader.dispose();

        } catch (Exception e) {
            System.err.println("Error reading file: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void printNode(Node node, int depth) {
        String indent = "  ".repeat(depth);
        System.out.print(indent + node.getNodeName());

        // Print attributes
        NamedNodeMap attributes = node.getAttributes();
        if (attributes != null && attributes.getLength() > 0) {
            System.out.print(" [");
            for (int i = 0; i < attributes.getLength(); i++) {
                Node attr = attributes.item(i);
                if (i > 0) System.out.print(", ");
                System.out.print(attr.getNodeName() + "=\"" + attr.getNodeValue() + "\"");
            }
            System.out.print("]");
        }

        // Print text content if present (skip for nodes with children)
        if (node.getChildNodes().getLength() == 0) {
            try {
                String textContent = node.getTextContent();
                if (textContent != null && !textContent.trim().isEmpty()) {
                    System.out.print(" = " + textContent.trim());
                }
            } catch (Exception e) {
                // Some nodes don't support getTextContent()
            }
        }

        System.out.println();

        // Print children
        for (Node child = node.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                printNode(child, depth + 1);
            }
        }
    }

    /**
     * Extracts DPI from image metadata (same as PsdColorSeparator)
     */
    private static int extractDPI(IIOMetadata metadata) {
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
                    int dpi = (int) Math.round(25.4 / pixelSizeMM);
                    System.out.println("  Found HorizontalPixelSize: " + pixelSizeMM + " mm -> " + dpi + " DPI");
                    return dpi;
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
                        int dpi = (int) Math.round(dpiDouble);
                        System.out.println("  Found " + name + ": " + value + " -> " + dpi + " DPI");
                        return dpi;
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
}
