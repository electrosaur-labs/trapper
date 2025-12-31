package org.electrosaur.trapper;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

/**
 * Generates small test PSD files for unit testing
 */
public class TestImageGenerator {

    /**
     * Creates a small test PSD file with known colors for trapping tests
     * Image: 100x100 pixels at 300 DPI
     * Colors: White, Yellow, Red, Blue (light to dark)
     * Layout: 4 quadrants with different colors
     */
    public static void createTestPSD(String filename) throws IOException {
        int width = 100;
        int height = 100;
        int dpi = 300;

        // Create image with 4 colored quadrants
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);

        // Top-left: White (255, 255, 255)
        fillQuadrant(image, 0, 0, width/2, height/2, new Color(255, 255, 255));

        // Top-right: Yellow (255, 255, 0)
        fillQuadrant(image, width/2, 0, width/2, height/2, new Color(255, 255, 0));

        // Bottom-left: Red (255, 0, 0)
        fillQuadrant(image, 0, height/2, width/2, height/2, new Color(255, 0, 0));

        // Bottom-right: Blue (0, 0, 255)
        fillQuadrant(image, width/2, height/2, width/2, height/2, new Color(0, 0, 255));

        // Write as PSD with resolution metadata
        writePSD(filename, image, dpi);
    }

    /**
     * Creates a more complex test PSD with overlapping colors
     * Image: 80x80 pixels at 300 DPI
     * Colors: White background, Yellow circle, Red square, Blue triangle
     */
    public static void createComplexTestPSD(String filename) throws IOException {
        int width = 80;
        int height = 80;
        int dpi = 300;

        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);

        // Fill with white background
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                image.setRGB(x, y, new Color(255, 255, 255).getRGB());
            }
        }

        // Draw yellow circle (center: 40,40, radius: 25)
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int dx = x - 40;
                int dy = y - 40;
                if (dx*dx + dy*dy <= 25*25) {
                    image.setRGB(x, y, new Color(255, 255, 0).getRGB());
                }
            }
        }

        // Draw red square (20x20 at position 50,10)
        fillQuadrant(image, 50, 10, 20, 20, new Color(255, 0, 0));

        // Draw blue triangle (vertices: (10,60), (30,60), (20,40))
        for (int y = 40; y <= 60; y++) {
            for (int x = 10; x <= 30; x++) {
                // Simple triangle fill logic
                if (isInTriangle(x, y, 10, 60, 30, 60, 20, 40)) {
                    image.setRGB(x, y, new Color(0, 0, 255).getRGB());
                }
            }
        }

        writePSD(filename, image, dpi);
    }

    private static void fillQuadrant(BufferedImage img, int startX, int startY, int w, int h, Color color) {
        int rgb = color.getRGB();
        for (int y = startY; y < startY + h && y < img.getHeight(); y++) {
            for (int x = startX; x < startX + w && x < img.getWidth(); x++) {
                img.setRGB(x, y, rgb);
            }
        }
    }

    private static boolean isInTriangle(int px, int py, int x1, int y1, int x2, int y2, int x3, int y3) {
        // Use barycentric coordinates
        int denom = ((y2 - y3)*(x1 - x3) + (x3 - x2)*(y1 - y3));
        if (denom == 0) return false;

        float a = ((y2 - y3)*(px - x3) + (x3 - x2)*(py - y3)) / (float)denom;
        float b = ((y3 - y1)*(px - x3) + (x1 - x3)*(py - y3)) / (float)denom;
        float c = 1 - a - b;

        return a >= 0 && b >= 0 && c >= 0;
    }

    private static void writePSD(String filename, BufferedImage image, int dpi) throws IOException {
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

            // Image Resources Section - include resolution
            ByteArrayOutputStream resStream = new ByteArrayOutputStream();
            DataOutputStream resData = new DataOutputStream(resStream);

            // Resolution Info (0x03ED)
            resData.writeBytes("8BIM");       // Signature
            resData.writeShort(0x03ED);       // Resource ID for resolution
            resData.writeShort(0);            // Name (empty pascal string)

            // Resolution data
            ByteArrayOutputStream resInfo = new ByteArrayOutputStream();
            DataOutputStream resInfoData = new DataOutputStream(resInfo);
            resInfoData.writeInt(dpi << 16);  // Horizontal resolution (fixed point)
            resInfoData.writeShort(1);        // Horizontal resolution unit (pixels per inch)
            resInfoData.writeShort(1);        // Width unit (inches)
            resInfoData.writeInt(dpi << 16);  // Vertical resolution (fixed point)
            resInfoData.writeShort(1);        // Vertical resolution unit (pixels per inch)
            resInfoData.writeShort(1);        // Height unit (inches)

            byte[] resInfoBytes = resInfo.toByteArray();
            resData.writeInt(resInfoBytes.length);
            resData.write(resInfoBytes);

            byte[] resBytes = resStream.toByteArray();
            dos.writeInt(resBytes.length);
            dos.write(resBytes);

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

    public static void main(String[] args) throws IOException {
        createTestPSD("src/test/resources/test-simple.psd");
        System.out.println("Created: src/test/resources/test-simple.psd");

        createComplexTestPSD("src/test/resources/test-complex.psd");
        System.out.println("Created: src/test/resources/test-complex.psd");
    }
}
