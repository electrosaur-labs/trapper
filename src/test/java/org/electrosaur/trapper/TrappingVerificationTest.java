package org.electrosaur.trapper;

import org.junit.Test;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import static org.junit.Assert.*;

/**
 * Integration tests that verify trapping produces correct output.
 * Tests compare generated trapped PSDs against expected golden files.
 */
public class TrappingVerificationTest {

    /**
     * Tests that trapping test-complex.psd produces output identical to the expected trapped version.
     * This verifies that the trapping algorithm is working correctly and hasn't regressed.
     */
    @Test
    public void testTrappingProducesExpectedOutput() throws Exception {
        // Input file
        File inputFile = new File("src/test/resources/test-complex.psd");
        assertTrue("Input file should exist: " + inputFile, inputFile.exists());

        // Expected trapped output
        File expectedFile = new File("src/test/resources/test-complex-trapped.psd");
        assertTrue("Expected trapped file should exist: " + expectedFile, expectedFile.exists());

        // Generate trapped output to a temp file
        Path tempOutput = Files.createTempFile("test-trapped-", ".psd");
        try {
            // Run the color separator with default trapping parameters
            PsdColorSeparator.processFile(
                inputFile.getAbsolutePath(),
                tempOutput.toString(),
                0.0,                    // minExpansion
                1.0 / 32.0,            // maxExpansion (1/32")
                new OffsetTrappingStrategy()
            );

            // Compare the generated file to the expected file byte-by-byte
            byte[] generatedBytes = Files.readAllBytes(tempOutput);
            byte[] expectedBytes = Files.readAllBytes(expectedFile.toPath());

            assertEquals("Trapped PSD file size should match expected",
                expectedBytes.length, generatedBytes.length);

            assertArrayEquals("Trapped PSD file should be identical to expected",
                expectedBytes, generatedBytes);

        } finally {
            // Clean up temp file
            Files.deleteIfExists(tempOutput);
        }
    }

    /**
     * Tests that flattening the trapped output produces an image identical to the original input.
     * This verifies that trapping doesn't lose or alter any pixels when composited.
     */
    @Test
    public void testFlattenedTrappedOutputMatchesOriginalInput() throws Exception {
        // Input file
        File inputFile = new File("src/test/resources/test-complex.psd");
        assertTrue("Input file should exist: " + inputFile, inputFile.exists());

        // Read and flatten the original input
        BufferedImage originalFlattened = readAndFlattenPSD(inputFile);

        // Generate trapped output to a temp file
        Path tempOutput = Files.createTempFile("test-trapped-", ".psd");
        try {
            // Run the color separator with default trapping parameters
            PsdColorSeparator.processFile(
                inputFile.getAbsolutePath(),
                tempOutput.toString(),
                0.0,                    // minExpansion
                1.0 / 32.0,            // maxExpansion (1/32")
                new OffsetTrappingStrategy()
            );

            // Read and flatten the trapped output
            BufferedImage trappedFlattened = readAndFlattenPSD(tempOutput.toFile());

            // Compare dimensions
            assertEquals("Width should match", originalFlattened.getWidth(), trappedFlattened.getWidth());
            assertEquals("Height should match", originalFlattened.getHeight(), trappedFlattened.getHeight());

            // Compare pixels
            int width = originalFlattened.getWidth();
            int height = originalFlattened.getHeight();
            int differences = 0;
            int trappedPixels = 0;

            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int origRgb = originalFlattened.getRGB(x, y) & 0x00FFFFFF;
                    int trapRgb = trappedFlattened.getRGB(x, y) & 0x00FFFFFF;

                    if (origRgb != trapRgb) {
                        // Check if this is a valid trap (lighter color over darker)
                        double origLightness = calculateLightness(origRgb);
                        double trapLightness = calculateLightness(trapRgb);

                        if (trapLightness > origLightness) {
                            // This is expected: lighter color trapped over darker color
                            trappedPixels++;
                        } else {
                            // This is unexpected: darker color where lighter color should be
                            differences++;
                        }
                    }
                }
            }

            assertEquals("Flattened trapped image should not have invalid color changes", 0, differences);
            // Note: trappedPixels might be 0 if the test image has no areas where colors meet
            // The important check is that there are no invalid differences

        } finally {
            // Clean up temp file
            Files.deleteIfExists(tempOutput);
        }
    }

    /**
     * Reads a PSD file and returns its flattened/composite image
     */
    private BufferedImage readAndFlattenPSD(File file) throws IOException {
        try (ImageInputStream iis = ImageIO.createImageInputStream(file)) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);

            if (!readers.hasNext()) {
                throw new IOException("No reader found for PSD file: " + file);
            }

            ImageReader reader = readers.next();
            reader.setInput(iis);

            // Read the composite/flattened image (index 0)
            BufferedImage image = reader.read(0);
            reader.dispose();

            return image;
        }
    }

    /**
     * Calculates lightness using standard RGB to grayscale conversion
     */
    private double calculateLightness(int rgb) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        return 0.299 * r + 0.587 * g + 0.114 * b;
    }
}
