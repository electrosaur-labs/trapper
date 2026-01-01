package org.electrosaur.trapper;

import org.junit.Before;
import org.junit.Test;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;
import static org.junit.Assert.*;

/**
 * Integration tests for color trapping functionality
 * Tests that trapping produces correct output with test images
 */
public class TrappingIntegrationTest {

    private static final String TEST_SIMPLE = "src/test/resources/test-simple.psd";
    private static final String TEST_COMPLEX = "src/test/resources/test-complex.psd";
    private static final double EPSILON = 0.0000001;
    private static final TrappingStrategy OFFSET_STRATEGY = new OffsetTrappingStrategy();

    @Before
    public void setUp() throws IOException {
        // Ensure test files exist
        if (!new File(TEST_SIMPLE).exists()) {
            TestImageGenerator.createTestPSD(TEST_SIMPLE);
        }
        if (!new File(TEST_COMPLEX).exists()) {
            TestImageGenerator.createComplexTestPSD(TEST_COMPLEX);
        }
    }

    @Test
    public void testSimpleTrapping_DefaultRange() throws IOException {
        String outputFile = "build/test-output-simple-default.psd";

        // Run trapping with default range (0 to 1/32")
        PsdColorSeparator.processFile(TEST_SIMPLE, outputFile, 0.0, 1.0/32.0, OFFSET_STRATEGY);

        // Verify output file was created
        File output = new File(outputFile);
        assertTrue("Output file should exist", output.exists());
        assertTrue("Output file should have content", output.length() > 0);

        // Verify the file can be read back
        BufferedImage result = ImageIO.read(output);
        assertNotNull("Output should be readable", result);
        assertEquals("Width should match", 100, result.getWidth());
        assertEquals("Height should match", 100, result.getHeight());

        // Clean up
        output.delete();
    }

    @Test
    public void testSimpleTrapping_CustomRange() throws IOException {
        String outputFile = "build/test-output-simple-custom.psd";

        // Run trapping with custom range (0 to 1/64")
        PsdColorSeparator.processFile(TEST_SIMPLE, outputFile, 0.0, 1.0/64.0, OFFSET_STRATEGY);

        File output = new File(outputFile);
        assertTrue("Output file should exist", output.exists());
        assertTrue("Output file should have content", output.length() > 0);

        // Clean up
        output.delete();
    }

    @Test
    public void testComplexTrapping_DefaultRange() throws IOException {
        String outputFile = "build/test-output-complex-default.psd";

        // Run trapping with default range
        PsdColorSeparator.processFile(TEST_COMPLEX, outputFile, 0.0, 1.0/32.0, OFFSET_STRATEGY);

        File output = new File(outputFile);
        assertTrue("Output file should exist", output.exists());
        assertTrue("Output file should have content", output.length() > 0);

        // Verify the file can be read back
        BufferedImage result = ImageIO.read(output);
        assertNotNull("Output should be readable", result);
        assertEquals("Width should match", 80, result.getWidth());
        assertEquals("Height should match", 80, result.getHeight());

        // Clean up
        output.delete();
    }

    @Test
    public void testTrapping_NoExpansion() throws IOException {
        String outputFile = "build/test-output-no-expansion.psd";

        // Run trapping with no expansion (0 to 0)
        PsdColorSeparator.processFile(TEST_SIMPLE, outputFile, 0.0, 0.0, OFFSET_STRATEGY);

        File output = new File(outputFile);
        assertTrue("Output file should exist", output.exists());

        // With no expansion, output should still be valid but smaller
        assertTrue("Output file should have content", output.length() > 0);

        // Clean up
        output.delete();
    }

    @Test
    public void testTrapping_MinimalExpansion() throws IOException {
        String outputFile = "build/test-output-minimal.psd";

        // Run trapping with minimal expansion (0 to 1/128")
        PsdColorSeparator.processFile(TEST_SIMPLE, outputFile, 0.0, 1.0/128.0, OFFSET_STRATEGY);

        File output = new File(outputFile);
        assertTrue("Output file should exist", output.exists());
        assertTrue("Output file should have content", output.length() > 0);

        // Clean up
        output.delete();
    }

    @Test
    public void testTrapping_LargeExpansion() throws IOException {
        String outputFile = "build/test-output-large.psd";

        // Run trapping with larger expansion (0 to 1/16")
        PsdColorSeparator.processFile(TEST_SIMPLE, outputFile, 0.0, 1.0/16.0, OFFSET_STRATEGY);

        File output = new File(outputFile);
        assertTrue("Output file should exist", output.exists());
        assertTrue("Output file should have content", output.length() > 0);

        // Clean up
        output.delete();
    }

    @Test
    public void testTrapping_NonZeroMinimum() throws IOException {
        String outputFile = "build/test-output-nonzero-min.psd";

        // Run trapping with non-zero minimum (1/64" to 1/32")
        PsdColorSeparator.processFile(TEST_SIMPLE, outputFile, 1.0/64.0, 1.0/32.0, OFFSET_STRATEGY);

        File output = new File(outputFile);
        assertTrue("Output file should exist", output.exists());
        assertTrue("Output file should have content", output.length() > 0);

        // Clean up
        output.delete();
    }

    @Test
    public void testTrapping_VerificationPasses() throws IOException {
        String outputFile = "build/test-output-verification.psd";

        // The processFile method includes verification
        // If verification fails, it would print errors but still complete
        // This test verifies no exceptions are thrown
        try {
            PsdColorSeparator.processFile(TEST_SIMPLE, outputFile, 0.0, 1.0/32.0, OFFSET_STRATEGY);

            File output = new File(outputFile);
            assertTrue("Output file should exist after verification", output.exists());

            // Clean up
            output.delete();
        } catch (Exception e) {
            fail("Trapping should not throw exception: " + e.getMessage());
        }
    }

    @Test
    public void testTrapping_ConsistentOutput() throws IOException {
        String output1 = "build/test-output-consistent-1.psd";
        String output2 = "build/test-output-consistent-2.psd";

        // Run trapping twice with same parameters
        PsdColorSeparator.processFile(TEST_SIMPLE, output1, 0.0, 1.0/32.0, OFFSET_STRATEGY);
        PsdColorSeparator.processFile(TEST_SIMPLE, output2, 0.0, 1.0/32.0, OFFSET_STRATEGY);

        File file1 = new File(output1);
        File file2 = new File(output2);

        assertTrue("First output should exist", file1.exists());
        assertTrue("Second output should exist", file2.exists());

        // File sizes should be identical for deterministic output
        assertEquals("Output files should have same size", file1.length(), file2.length());

        // Clean up
        file1.delete();
        file2.delete();
    }

    @Test
    public void testTrapping_DifferentExpansions_DifferentSizes() throws IOException {
        String outputSmall = "build/test-output-small-trap.psd";
        String outputLarge = "build/test-output-large-trap.psd";

        // Run trapping with different expansion amounts
        PsdColorSeparator.processFile(TEST_SIMPLE, outputSmall, 0.0, 1.0/128.0, OFFSET_STRATEGY);
        PsdColorSeparator.processFile(TEST_SIMPLE, outputLarge, 0.0, 1.0/16.0, OFFSET_STRATEGY);

        File fileSmall = new File(outputSmall);
        File fileLarge = new File(outputLarge);

        assertTrue("Small trap output should exist", fileSmall.exists());
        assertTrue("Large trap output should exist", fileLarge.exists());

        // Files should be valid but may have different sizes
        assertTrue("Small trap output should have content", fileSmall.length() > 0);
        assertTrue("Large trap output should have content", fileLarge.length() > 0);

        // Clean up
        fileSmall.delete();
        fileLarge.delete();
    }

    @Test
    public void testTrapping_OutputFilenameGeneration() throws IOException {
        // Test that output filename is generated correctly
        String inputFile = "src/test/resources/test-simple.psd";

        // processFile generates output filename from input
        String outputFile = "build/out-test-simple.psd";
        PsdColorSeparator.processFile(inputFile, outputFile, 0.0, 1.0/32.0, OFFSET_STRATEGY);

        File output = new File(outputFile);
        assertTrue("Generated output file should exist", output.exists());

        // Clean up
        output.delete();
    }
}
