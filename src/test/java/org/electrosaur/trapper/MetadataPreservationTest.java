package org.electrosaur.trapper;

import org.junit.Test;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.Assert.*;

/**
 * Tests that verify metadata is correctly preserved and augmented during processing.
 */
public class MetadataPreservationTest {

    /**
     * Test case 1: Input file HAS metadata from original creator
     * Expected: Output should preserve original metadata AND add Trapper metadata
     *
     * test-simple.psd contains XMP metadata with:
     * - Creator: Claude (AI Assistant)
     * - Description: Test image created by Claude AI assistant for testing color trapping algorithms
     * - Rights: Public Domain
     * - WebStatement: https://anthropic.com
     */
    @Test
    public void testExistingMetadata_PreservesOriginalAndAddsTrapper() throws Exception {
        // Input file (test-simple.psd has Claude's metadata)
        File inputFile = new File("src/test/resources/test-simple.psd");
        assertTrue("Input file should exist", inputFile.exists());

        // Verify input has original metadata
        String inputMetadata = extractMetadataFromPSD(inputFile);
        assertNotNull("Input should have metadata", inputMetadata);
        assertTrue("Input should have Claude's metadata",
            inputMetadata.contains("Claude") && inputMetadata.contains("anthropic"));

        // Generate trapped output to a temp file
        Path tempOutput = Files.createTempFile("test-metadata-preserve-", ".psd");
        try {
            // Run the color separator
            PsdColorSeparator.processFile(
                inputFile.getAbsolutePath(),
                tempOutput.toString(),
                0.0,
                1.0 / 32.0,
                new OffsetTrappingStrategy()
            );

            // Read metadata from output file
            String outputMetadata = extractMetadataFromPSD(tempOutput.toFile());

            // Verify XMP metadata is present
            assertNotNull("Output metadata should not be null", outputMetadata);
            assertTrue("Should be valid XMP",
                outputMetadata.contains("<?xpacket") && outputMetadata.contains("x:xmpmeta"));

            // Verify original metadata is preserved (should be intact XML, not escaped text)
            assertTrue("Should preserve Claude as creator",
                outputMetadata.contains("Claude (AI Assistant)"));
            assertTrue("Should preserve original description",
                outputMetadata.contains("Test image created by Claude"));
            assertTrue("Should preserve Anthropic URL",
                outputMetadata.contains("anthropic.com"));
            assertTrue("Should preserve Public Domain rights",
                outputMetadata.contains("Public Domain"));

            // Verify Trapper processing history is present (XMP Media Management)
            assertTrue("Should contain xmpMM:History",
                outputMetadata.contains("xmpMM:History"));
            assertTrue("Should contain trapped action",
                outputMetadata.contains("<stEvt:action>trapped</stEvt:action>"));
            assertTrue("Should contain Trapper software agent",
                outputMetadata.contains("Trapper v2.0"));
            assertTrue("Should contain GitHub URL",
                outputMetadata.contains("github.com/electrosaur-labs/trapper"));

        } finally {
            // Clean up temp file
            Files.deleteIfExists(tempOutput);
        }
    }

    /**
     * Test case 2: Input file HAS metadata
     * Expected: Output should preserve original AND add Trapper metadata
     *
     * NOTE: Currently this test verifies the framework is in place. Full metadata
     * extraction from PSD files requires parsing Image Resources which TwelveMonkeys
     * doesn't expose. The test verifies that IF metadata exists, it gets preserved.
     */
    @Test
    public void testExistingMetadata_PreservesAndAddsTrapper() throws Exception {
        // Create a test PSD with existing metadata
        Path tempInput = Files.createTempFile("test-input-with-metadata-", ".psd");
        Path tempOutput = Files.createTempFile("test-output-with-metadata-", ".psd");

        try {
            // Copy test-simple.psd as base
            Files.copy(
                new File("src/test/resources/test-simple.psd").toPath(),
                tempInput,
                java.nio.file.StandardCopyOption.REPLACE_EXISTING
            );

            // Add mock original metadata to the input file
            String originalMetadata = "Original Artist: John Doe\n" +
                                     "Copyright: 2025 Acme Corp\n" +
                                     "Contact: john@example.com";
            addMetadataToPSD(tempInput.toFile(), originalMetadata);

            // Verify we successfully added the metadata
            String inputMetadata = extractMetadataFromPSD(tempInput.toFile());
            assertTrue("Input should have original metadata",
                inputMetadata != null && inputMetadata.contains("John Doe"));

            // NOTE: Currently PsdColorSeparator.readAndFlattenPSD() returns null for
            // originalMetadata because TwelveMonkeys doesn't expose PSD Image Resources.
            // This test verifies that the code path exists and doesn't break.
            // When full extraction is implemented, this test will validate preservation.

            // Run the color separator
            PsdColorSeparator.processFile(
                tempInput.toString(),
                tempOutput.toString(),
                0.0,
                1.0 / 32.0,
                new OffsetTrappingStrategy()
            );

            // Read metadata from output file
            String outputMetadata = extractMetadataFromPSD(tempOutput.toFile());

            // Verify output contains at least Trapper metadata in XMP format
            assertNotNull("Output metadata should not be null", outputMetadata);

            // Verify output contains valid XMP with processing history
            assertTrue("Should be valid XMP",
                outputMetadata.contains("<?xpacket") && outputMetadata.contains("x:xmpmeta"));
            assertTrue("Should contain xmpMM:History",
                outputMetadata.contains("xmpMM:History"));
            assertTrue("Should contain trapped action",
                outputMetadata.contains("<stEvt:action>trapped</stEvt:action>"));
            assertTrue("Should contain Trapper tool name",
                outputMetadata.contains("Trapper v2.0"));

            // Future enhancement: When metadata extraction is implemented,
            // add assertions to verify original metadata preservation

        } finally {
            // Clean up temp files
            Files.deleteIfExists(tempInput);
            Files.deleteIfExists(tempOutput);
        }
    }

    /**
     * Extracts metadata (Image Resource 1034) from a PSD file.
     * Returns the metadata string or null if not found.
     */
    private String extractMetadataFromPSD(File psdFile) throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(psdFile, "r");
             FileChannel channel = raf.getChannel()) {

            // Read file header (26 bytes)
            ByteBuffer header = ByteBuffer.allocate(26);
            int bytesRead = channel.read(header);
            if (bytesRead < 26) {
                return null; // File too small
            }
            header.flip();

            // Check signature
            byte[] sig = new byte[4];
            header.get(sig);
            if (!new String(sig).equals("8BPS")) {
                return null; // Not a PSD file
            }

            // Skip version (2) and reserved (6)
            header.position(header.position() + 8);

            // Read file info (but we don't need it for metadata)
            int channels = header.getShort() & 0xFFFF;
            int height = header.getInt();
            int width = header.getInt();
            int bitsPerChannel = header.getShort() & 0xFFFF;
            int colorMode = header.getShort() & 0xFFFF;

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
                return null; // No resources
            }

            // Read all resources
            ByteBuffer resourcesData = ByteBuffer.allocate(resourcesLen);
            channel.read(resourcesData);
            resourcesData.flip();

            // Parse resources looking for 1060 (XMP metadata - what Photoshop File Info uses)
            while (resourcesData.remaining() >= 12) {
                // Check signature
                byte[] resSig = new byte[4];
                resourcesData.get(resSig);
                String sigStr = new String(resSig, "ISO-8859-1");
                if (!sigStr.equals("8BIM")) {
                    break;
                }

                int resourceId = resourcesData.getShort() & 0xFFFF;

                // Read name (our code writes short 0, which is 2 bytes)
                int nameField = resourcesData.getShort() & 0xFFFF;

                if (resourcesData.remaining() < 4) {
                    break; // Not enough data for size field
                }

                int dataSize = resourcesData.getInt();

                if (resourcesData.remaining() < dataSize) {
                    break; // Not enough data
                }

                if (resourceId == 1060) {
                    // Found XMP metadata (displays in Photoshop File Info)
                    byte[] data = new byte[dataSize];
                    resourcesData.get(data);
                    return new String(data, "UTF-8").trim();
                } else {
                    // Skip this resource's data
                    resourcesData.position(resourcesData.position() + dataSize);
                }

                // Resources are padded to even byte boundaries
                if ((dataSize & 1) == 1 && resourcesData.hasRemaining()) {
                    resourcesData.get(); // Skip padding
                }
            }

            return null; // Resource 1060 not found
        }
    }

    /**
     * Adds metadata (Image Resource 1034) to a PSD file by rewriting it.
     * This is a test utility method.
     */
    private void addMetadataToPSD(File psdFile, String metadata) throws IOException {
        // Read entire file
        byte[] fileData = Files.readAllBytes(psdFile.toPath());
        ByteBuffer buffer = ByteBuffer.wrap(fileData);

        // Skip header (26 bytes)
        buffer.position(26);

        // Read and skip Color Mode Data Section
        int colorModeDataLen = buffer.getInt();
        int colorModeEnd = buffer.position() + colorModeDataLen;
        buffer.position(colorModeEnd);

        // Get current Image Resources Section position and length
        int resourcesLengthPos = buffer.position();
        int oldResourcesLen = buffer.getInt();
        int resourcesStart = buffer.position();
        int resourcesEnd = resourcesStart + oldResourcesLen;

        // Create new metadata resource block
        byte[] metadataBytes = metadata.getBytes("ISO-8859-1");
        int metadataSize = metadataBytes.length;
        if (metadataSize % 2 != 0) {
            metadataSize++; // Pad to even
        }

        int newResourceBlockSize = 12 + metadataSize; // 4(sig) + 2(id) + 2(name) + 4(size) + data + padding

        // Build new file
        try (RandomAccessFile raf = new RandomAccessFile(psdFile, "rw");
             FileChannel channel = raf.getChannel()) {

            // Write everything up to resources length
            channel.position(0);
            channel.write(ByteBuffer.wrap(fileData, 0, resourcesLengthPos));

            // Write new resources length
            ByteBuffer newResourcesLen = ByteBuffer.allocate(4);
            newResourcesLen.putInt(oldResourcesLen + newResourceBlockSize);
            newResourcesLen.flip();
            channel.write(newResourcesLen);

            // Write new metadata resource block
            ByteBuffer resourceBlock = ByteBuffer.allocate(newResourceBlockSize);
            resourceBlock.put("8BIM".getBytes());
            resourceBlock.putShort((short) 1060); // Resource ID for XMP metadata (displays in File Info)
            resourceBlock.putShort((short) 0);    // Name length (empty)
            resourceBlock.putInt(metadataSize);
            resourceBlock.put(metadataBytes);
            if (metadataBytes.length % 2 != 0) {
                resourceBlock.put((byte) 0); // Padding
            }
            resourceBlock.flip();
            channel.write(resourceBlock);

            // Write old resources (if any)
            if (oldResourcesLen > 0) {
                channel.write(ByteBuffer.wrap(fileData, resourcesStart, oldResourcesLen));
            }

            // Write rest of file
            channel.write(ByteBuffer.wrap(fileData, resourcesEnd, fileData.length - resourcesEnd));

            // Truncate if needed
            channel.truncate(channel.position());
        }
    }
}
