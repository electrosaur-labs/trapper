package org.electrosaur.trapper;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Iterator;

/**
 * Utility to resize PSD files by scaling down the dimensions.
 * Useful for creating smaller test files for source control.
 */
public class PsdResizer {

    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: java PsdResizer <input.psd> <scale_factor>");
            System.err.println("  scale_factor: decimal factor to scale by (e.g., 0.5 for half size, 0.25 for quarter size)");
            System.err.println("Example: java PsdResizer input.psd 0.25");
            System.exit(1);
        }

        String inputFile = args[0];
        double scaleFactor;

        try {
            scaleFactor = Double.parseDouble(args[1]);
            if (scaleFactor <= 0 || scaleFactor >= 1) {
                System.err.println("Error: scale_factor must be between 0 and 1 (exclusive)");
                System.exit(1);
            }
        } catch (NumberFormatException e) {
            System.err.println("Error: Invalid scale_factor: " + args[1]);
            System.exit(1);
            return;
        }

        String outputFile = generateOutputFilename(inputFile, scaleFactor);

        try {
            resizePSD(inputFile, outputFile, scaleFactor);
            System.out.println("Successfully created resized PSD: " + outputFile);

            File input = new File(inputFile);
            File output = new File(outputFile);
            System.out.printf("Original size: %.2f MB%n", input.length() / (1024.0 * 1024.0));
            System.out.printf("Resized size: %.2f MB%n", output.length() / (1024.0 * 1024.0));
            System.out.printf("Size reduction: %.1f%%%n",
                (1 - (double)output.length() / input.length()) * 100);
        } catch (Exception e) {
            System.err.println("Error resizing PSD file: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static String generateOutputFilename(String inputFile, double scaleFactor) {
        File file = new File(inputFile);
        String name = file.getName();
        String parent = file.getParent();

        // Remove .psd extension if present
        if (name.toLowerCase().endsWith(".psd")) {
            name = name.substring(0, name.length() - 4);
        }

        int scalePercent = (int)(scaleFactor * 100);
        String outputName = name + "-" + scalePercent + "pct.psd";

        return parent != null ? parent + File.separator + outputName : outputName;
    }

    private static void resizePSD(String inputFile, String outputFile, double scaleFactor) throws IOException {
        // Read the PSD file
        File file = new File(inputFile);
        BufferedImage originalImage;

        try (ImageInputStream iis = ImageIO.createImageInputStream(file)) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);

            if (!readers.hasNext()) {
                throw new IOException("No reader found for PSD file");
            }

            ImageReader reader = readers.next();
            reader.setInput(iis);

            // Read the composite/flattened image
            originalImage = reader.read(0);
            reader.dispose();
        }

        int originalWidth = originalImage.getWidth();
        int originalHeight = originalImage.getHeight();
        int newWidth = (int)(originalWidth * scaleFactor);
        int newHeight = (int)(originalHeight * scaleFactor);

        System.out.println("Original dimensions: " + originalWidth + "x" + originalHeight);
        System.out.println("Resized dimensions: " + newWidth + "x" + newHeight);
        System.out.printf("Scale factor: %.2f (%.0f%%)%n", scaleFactor, scaleFactor * 100);

        // Create resized image with high-quality scaling
        BufferedImage resizedImage = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = resizedImage.createGraphics();

        // Use high-quality rendering hints
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        g2d.drawImage(originalImage, 0, 0, newWidth, newHeight, null);
        g2d.dispose();

        // Write as a simple single-layer PSD
        writePSD(outputFile, newWidth, newHeight, resizedImage);
    }

    private static void writePSD(String filename, int width, int height, BufferedImage image) throws IOException {
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
            dos.writeInt(0);                  // No layers

            // Image Data Section (merged/flattened composite image)
            dos.writeShort(1);  // Compression: RLE
            writeImageData(dos, image);

            // Write to file
            channel.write(ByteBuffer.wrap(baos.toByteArray()));
        }
    }

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

    private static void writeImageData(DataOutputStream dos, BufferedImage img) throws IOException {
        int width = img.getWidth();
        int height = img.getHeight();

        // Compress all channels
        byte[][][] allCompressedRows = new byte[3][][];
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
            allCompressedRows[channel] = compressedRows;
        }

        // Write byte counts for all scanlines of all channels
        for (int channel = 0; channel < 3; channel++) {
            byte[][] compressedRows = allCompressedRows[channel];
            for (int y = 0; y < height; y++) {
                dos.writeShort(compressedRows[y].length);
            }
        }

        // Write compressed data for all channels
        for (int channel = 0; channel < 3; channel++) {
            byte[][] compressedRows = allCompressedRows[channel];
            for (int y = 0; y < height; y++) {
                dos.write(compressedRows[y]);
            }
        }
    }
}
