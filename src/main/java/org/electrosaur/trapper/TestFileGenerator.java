package org.electrosaur.trapper;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Random;

/**
 * Generates test PSD files with specific colors for trapping tests
 */
public class TestFileGenerator {

    // Standard print colors: White, Red, Green, Blue, Cyan, Magenta, Yellow, Black
    private static final Color[] TEST_COLORS = {
        new Color(255, 255, 255), // White (W)
        new Color(255, 0, 0),     // Red (R)
        new Color(0, 255, 0),     // Green (G)
        new Color(0, 0, 255),     // Blue (B)
        new Color(0, 255, 255),   // Cyan (C)
        new Color(255, 0, 255),   // Magenta (M)
        new Color(255, 255, 0),   // Yellow (Y)
        new Color(0, 0, 0)        // Black (K)
    };

    public static void main(String[] args) {
        String filename = "in-test.psd";
        int width = 200;
        int height = 200;

        if (args.length >= 1) {
            filename = args[0];
            if (!filename.startsWith("in-")) {
                filename = "in-" + filename;
            }
            if (!filename.endsWith(".psd")) {
                filename += ".psd";
            }
        }

        if (args.length >= 3) {
            try {
                width = Integer.parseInt(args[1]);
                height = Integer.parseInt(args[2]);
            } catch (NumberFormatException e) {
                System.err.println("Invalid dimensions, using default 200x200");
            }
        }

        try {
            generateTestFile(filename, width, height);
            System.out.println("Generated test file: " + filename);
            System.out.println("Dimensions: " + width + "x" + height);
            System.out.println("Colors: W, R, G, B, C, M, Y, K");
        } catch (IOException e) {
            System.err.println("Error generating test file: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    /**
     * Generates a PSD file with randomly colored pixels using R, G, B, C, M, Y, K
     */
    public static void generateTestFile(String filename, int width, int height) throws IOException {
        Random random = new Random(42); // Fixed seed for reproducibility

        // Create image with random colors
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                Color color = TEST_COLORS[random.nextInt(TEST_COLORS.length)];
                image.setRGB(x, y, color.getRGB());
            }
        }

        // Write as simple single-layer PSD
        writeSingleLayerPSD(filename, image);
    }

    /**
     * Writes a simple single-layer PSD file
     */
    private static void writeSingleLayerPSD(String filename, BufferedImage image) throws IOException {
        int width = image.getWidth();
        int height = image.getHeight();

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

            // Image Data Section (flattened composite image)
            dos.writeShort(0);  // Compression: Raw
            writeImageData(dos, image);

            // Write to file
            channel.write(ByteBuffer.wrap(baos.toByteArray()));
        }
    }

    private static void writeImageData(DataOutputStream dos, BufferedImage img) throws IOException {
        int width = img.getWidth();
        int height = img.getHeight();

        // Write interleaved RGB data
        for (int channel = 0; channel < 3; channel++) {
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int rgb = img.getRGB(x, y);
                    int value;
                    if (channel == 0) value = (rgb >> 16) & 0xFF;      // Red
                    else if (channel == 1) value = (rgb >> 8) & 0xFF;  // Green
                    else value = rgb & 0xFF;                           // Blue
                    dos.writeByte(value);
                }
            }
        }
    }
}
