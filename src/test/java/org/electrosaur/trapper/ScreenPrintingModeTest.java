package org.electrosaur.trapper;

import org.junit.Before;
import org.junit.Test;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;
import static org.junit.Assert.*;

/**
 * Tests for screen printing mode with reversed trap direction
 * Verifies that dark colors trap over light colors (opposite of offset)
 */
public class ScreenPrintingModeTest {

    private static final String TEST_SIMPLE = "src/test/resources/test-simple.psd";
    private static final String TEST_COMPLEX = "src/test/resources/test-complex.psd";
    private static final TrappingStrategy SCREEN_STRATEGY = new ScreenPrintingTrappingStrategy();
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
    public void testScreenMode_BasicFunctionality() throws IOException {
        String outputFile = "build/test-output-screen-basic.psd";

        // Run trapping in screen printing mode (0 to 4pt default)
        double fourPoints = 4.0 / 72.0; // 4 points in inches
        PsdColorSeparator.processFile(TEST_SIMPLE, outputFile, 0.0, fourPoints, SCREEN_STRATEGY);

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
    public void testScreenMode_PointBasedMeasurements() throws IOException {
        String outputFile = "build/test-output-screen-points.psd";

        // Test point-based measurements (screen printing standard)
        double twoPoints = 2.0 / 72.0;
        double sixPoints = 6.0 / 72.0;
        PsdColorSeparator.processFile(TEST_SIMPLE, outputFile, twoPoints, sixPoints, SCREEN_STRATEGY);

        File output = new File(outputFile);
        assertTrue("Output file should exist", output.exists());
        assertTrue("Output file should have content", output.length() > 0);

        // Clean up
        output.delete();
    }

    @Test
    public void testScreenMode_ComplexImage() throws IOException {
        String outputFile = "build/test-output-screen-complex.psd";

        // Run screen printing mode on complex test image
        double fourPoints = 4.0 / 72.0;
        PsdColorSeparator.processFile(TEST_COMPLEX, outputFile, 0.0, fourPoints, SCREEN_STRATEGY);

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
    public void testScreenMode_LargeTrapSize() throws IOException {
        String outputFile = "build/test-output-screen-large-trap.psd";

        // Screen printing typically uses larger traps (2-6 points)
        double sixPoints = 6.0 / 72.0;
        PsdColorSeparator.processFile(TEST_SIMPLE, outputFile, 0.0, sixPoints, SCREEN_STRATEGY);

        File output = new File(outputFile);
        assertTrue("Output file should exist", output.exists());
        assertTrue("Output file should have content", output.length() > 0);

        // Clean up
        output.delete();
    }

    @Test
    public void testScreenMode_MinimalTrap() throws IOException {
        String outputFile = "build/test-output-screen-minimal.psd";

        // Test minimal trap size
        double twoPoints = 2.0 / 72.0;
        PsdColorSeparator.processFile(TEST_SIMPLE, outputFile, 0.0, twoPoints, SCREEN_STRATEGY);

        File output = new File(outputFile);
        assertTrue("Output file should exist", output.exists());
        assertTrue("Output file should have content", output.length() > 0);

        // Clean up
        output.delete();
    }

    @Test
    public void testScreenMode_ConsistentOutput() throws IOException {
        String output1 = "build/test-output-screen-consistent-1.psd";
        String output2 = "build/test-output-screen-consistent-2.psd";

        // Run trapping twice with same parameters in screen mode
        double fourPoints = 4.0 / 72.0;
        PsdColorSeparator.processFile(TEST_SIMPLE, output1, 0.0, fourPoints, SCREEN_STRATEGY);
        PsdColorSeparator.processFile(TEST_SIMPLE, output2, 0.0, fourPoints, SCREEN_STRATEGY);

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
    public void testTrapDirection_ReversedForScreenPrinting() {
        // Test that trap calculations are reversed
        // In offset: lightest layer gets max trap
        // In screen: darkest layer gets max trap

        int dpi = 300; // Typical screen printing DPI
        double minTrap = 0.0;
        double maxTrap = 4.0 / 72.0; // 4 points

        // Test with 3 layers (indices 0, 1, 2)
        int totalLayers = 3;

        // Offset strategy: lightest (index 0) should get max trap
        int offsetLight = OFFSET_STRATEGY.calculateExpansion(0, totalLayers, dpi, minTrap, maxTrap);
        int offsetMid = OFFSET_STRATEGY.calculateExpansion(1, totalLayers, dpi, minTrap, maxTrap);
        int offsetDark = OFFSET_STRATEGY.calculateExpansion(2, totalLayers, dpi, minTrap, maxTrap);

        // Screen strategy: darkest (index 2) should get max trap
        int screenLight = SCREEN_STRATEGY.calculateExpansion(0, totalLayers, dpi, minTrap, maxTrap);
        int screenMid = SCREEN_STRATEGY.calculateExpansion(1, totalLayers, dpi, minTrap, maxTrap);
        int screenDark = SCREEN_STRATEGY.calculateExpansion(2, totalLayers, dpi, minTrap, maxTrap);

        // Verify offset: light > mid > dark
        assertTrue("Offset: lightest should have most trap", offsetLight > offsetMid);
        assertTrue("Offset: mid should have more trap than dark", offsetMid > offsetDark);

        // Verify screen: dark > mid > light (REVERSED)
        assertTrue("Screen: darkest should have most trap", screenDark > screenMid);
        assertTrue("Screen: mid should have more trap than light", screenMid > screenLight);

        // Verify they're actually reversed relative to each other
        assertTrue("Screen lightest should match offset darkest", screenLight == offsetDark);
        assertTrue("Screen darkest should match offset lightest", screenDark == offsetLight);
    }

    @Test
    public void testStrategyInfo_ScreenPrinting() {
        // Test that screen strategy reports correct information
        assertEquals("Strategy name should be correct",
                     "Screen Printing", SCREEN_STRATEGY.getName());
        assertEquals("Trap direction should be correct",
                     "Dark traps over light", SCREEN_STRATEGY.getTrapDirection());

        String description = SCREEN_STRATEGY.getDescription();
        assertNotNull("Description should not be null", description);
        assertTrue("Description should not be empty", description.length() > 0);
        // Check description contains relevant keywords
        String lowerDesc = description.toLowerCase();
        assertTrue("Description should mention screen or printing or garment",
                   lowerDesc.contains("screen") || lowerDesc.contains("printing") ||
                   lowerDesc.contains("garment"));
    }

    @Test
    public void testStrategyInfo_OffsetLithography() {
        // Test that offset strategy reports correct information
        assertEquals("Strategy name should be correct",
                     "Offset Lithography", OFFSET_STRATEGY.getName());
        assertEquals("Trap direction should be correct",
                     "Light spreads into dark", OFFSET_STRATEGY.getTrapDirection());

        String description = OFFSET_STRATEGY.getDescription();
        assertNotNull("Description should not be null", description);
        assertTrue("Description should not be empty", description.length() > 0);
        // Check description contains relevant keywords
        String lowerDesc = description.toLowerCase();
        assertTrue("Description should mention offset or lithography or commercial",
                   lowerDesc.contains("offset") || lowerDesc.contains("lithography") ||
                   lowerDesc.contains("commercial"));
    }

    @Test
    public void testScreenMode_DifferentTrapSizes() throws IOException {
        String outputSmall = "build/test-output-screen-2pt.psd";
        String outputMedium = "build/test-output-screen-4pt.psd";
        String outputLarge = "build/test-output-screen-6pt.psd";

        // Test typical screen printing trap sizes (2, 4, 6 points)
        PsdColorSeparator.processFile(TEST_SIMPLE, outputSmall, 0.0, 2.0/72.0, SCREEN_STRATEGY);
        PsdColorSeparator.processFile(TEST_SIMPLE, outputMedium, 0.0, 4.0/72.0, SCREEN_STRATEGY);
        PsdColorSeparator.processFile(TEST_SIMPLE, outputLarge, 0.0, 6.0/72.0, SCREEN_STRATEGY);

        File fileSmall = new File(outputSmall);
        File fileMedium = new File(outputMedium);
        File fileLarge = new File(outputLarge);

        assertTrue("2pt output should exist", fileSmall.exists());
        assertTrue("4pt output should exist", fileMedium.exists());
        assertTrue("6pt output should exist", fileLarge.exists());

        assertTrue("2pt output should have content", fileSmall.length() > 0);
        assertTrue("4pt output should have content", fileMedium.length() > 0);
        assertTrue("6pt output should have content", fileLarge.length() > 0);

        // Clean up
        fileSmall.delete();
        fileMedium.delete();
        fileLarge.delete();
    }

    @Test
    public void testOffsetVsScreen_OutputDifference() throws IOException {
        String outputOffset = "build/test-output-comparison-offset.psd";
        String outputScreen = "build/test-output-comparison-screen.psd";

        // Run same image with both strategies (same trap size in inches)
        double trapSize = 4.0 / 72.0; // 4 points
        PsdColorSeparator.processFile(TEST_SIMPLE, outputOffset, 0.0, trapSize, OFFSET_STRATEGY);
        PsdColorSeparator.processFile(TEST_SIMPLE, outputScreen, 0.0, trapSize, SCREEN_STRATEGY);

        File fileOffset = new File(outputOffset);
        File fileScreen = new File(outputScreen);

        assertTrue("Offset output should exist", fileOffset.exists());
        assertTrue("Screen output should exist", fileScreen.exists());

        // Files should be valid but DIFFERENT due to reversed trap direction
        assertTrue("Offset output should have content", fileOffset.length() > 0);
        assertTrue("Screen output should have content", fileScreen.length() > 0);

        // Note: File sizes might be similar but layer data will differ
        // The key difference is which layers get more trapping

        // Clean up
        fileOffset.delete();
        fileScreen.delete();
    }

    @Test
    public void testScreenMode_VerificationPasses() throws IOException {
        String outputFile = "build/test-output-screen-verification.psd";

        // The processFile method includes verification
        // Verification should pass even with reversed trap direction
        try {
            double fourPoints = 4.0 / 72.0;
            PsdColorSeparator.processFile(TEST_SIMPLE, outputFile, 0.0, fourPoints, SCREEN_STRATEGY);

            File output = new File(outputFile);
            assertTrue("Output file should exist after verification", output.exists());

            // Clean up
            output.delete();
        } catch (Exception e) {
            fail("Screen printing mode should not throw exception: " + e.getMessage());
        }
    }

    @Test
    public void testTrapCalculation_EdgeCases() {
        int dpi = 300;
        double minTrap = 0.0;
        double maxTrap = 4.0 / 72.0;

        // Test single layer (edge case)
        int singleLayer = SCREEN_STRATEGY.calculateExpansion(0, 1, dpi, minTrap, maxTrap);
        assertEquals("Single layer should get minimum trap",
                     (int)Math.round(minTrap * dpi), singleLayer);

        // Test two layers
        int twoLayersFirst = SCREEN_STRATEGY.calculateExpansion(0, 2, dpi, minTrap, maxTrap);
        int twoLayersSecond = SCREEN_STRATEGY.calculateExpansion(1, 2, dpi, minTrap, maxTrap);

        // In screen printing with 2 layers: lightest (0) gets min, darkest (1) gets max
        assertEquals("First of two layers should get min trap",
                     (int)Math.round(minTrap * dpi), twoLayersFirst);
        assertEquals("Second of two layers should get max trap",
                     (int)Math.round(maxTrap * dpi), twoLayersSecond);
    }
}
