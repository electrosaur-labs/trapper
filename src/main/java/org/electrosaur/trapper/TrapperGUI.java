package org.electrosaur.trapper;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.stream.ImageInputStream;
import java.util.Iterator;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

/**
 * Simple Swing GUI for Trapper color trapping application.
 *
 * Provides a user-friendly interface for:
 * - Selecting input PSD files
 * - Choosing trapping mode (offset/screen)
 * - Setting trap sizes
 * - Processing files with progress feedback
 */
public class TrapperGUI extends JFrame {

    // Components
    private JTextField inputFileField;
    private JTextField outputFileField;
    private JComboBox<String> modeComboBox;
    private JTextField minTrapField;
    private JTextField maxTrapField;
    private JComboBox<String> unitComboBox;
    private JTextArea logArea;
    private JButton processButton;
    private JProgressBar progressBar;

    // Metadata display labels
    private JLabel metadataDimensionsLabel;
    private JLabel metadataDpiLabel;
    private JLabel metadataLayersLabel;
    private JLabel metadataColorsLabel;

    // Strategy instances
    private final OffsetTrappingStrategy offsetStrategy = new OffsetTrappingStrategy();
    private final ScreenPrintingTrappingStrategy screenStrategy = new ScreenPrintingTrappingStrategy();

    // Store original streams for restoration
    private final PrintStream originalOut = System.out;
    private final PrintStream originalErr = System.err;

    public TrapperGUI() {
        setTitle("Trapper - Color Trapping Tool");
        setSize(700, 650);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        initComponents();
        redirectSystemStreams();
    }

    /**
     * Custom OutputStream that redirects to the log text area.
     */
    private class LogOutputStream extends OutputStream {
        private final StringBuilder buffer = new StringBuilder();

        @Override
        public void write(int b) throws IOException {
            char c = (char) b;
            buffer.append(c);

            // Flush on newline
            if (c == '\n') {
                flush();
            }
        }

        @Override
        public void flush() {
            String text = buffer.toString();
            if (!text.isEmpty()) {
                SwingUtilities.invokeLater(() -> {
                    logArea.append(text);
                    logArea.setCaretPosition(logArea.getDocument().getLength());
                });
                buffer.setLength(0);
            }
        }
    }

    /**
     * Redirect System.out and System.err to the log area.
     */
    private void redirectSystemStreams() {
        LogOutputStream logStream = new LogOutputStream();
        System.setOut(new PrintStream(logStream, true));
        System.setErr(new PrintStream(logStream, true));
    }

    private void initComponents() {
        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        mainPanel.setBorder(new EmptyBorder(10, 10, 10, 10));

        // File Selection Panel
        mainPanel.add(createFileSelectionPanel());
        mainPanel.add(Box.createVerticalStrut(10));

        // File Metadata Panel
        mainPanel.add(createMetadataPanel());
        mainPanel.add(Box.createVerticalStrut(10));

        // Mode Selection Panel
        mainPanel.add(createModeSelectionPanel());
        mainPanel.add(Box.createVerticalStrut(10));

        // Trap Size Panel
        mainPanel.add(createTrapSizePanel());
        mainPanel.add(Box.createVerticalStrut(10));

        // Process Button
        mainPanel.add(createProcessPanel());
        mainPanel.add(Box.createVerticalStrut(10));

        // Progress Bar
        progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);
        progressBar.setString("Ready");
        mainPanel.add(progressBar);
        mainPanel.add(Box.createVerticalStrut(10));

        // Log Area
        mainPanel.add(createLogPanel());

        add(mainPanel);
    }

    private JPanel createFileSelectionPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(new TitledBorder("File Selection"));

        // Input file
        JPanel inputPanel = new JPanel(new BorderLayout(5, 5));
        inputPanel.add(new JLabel("Input PSD:"), BorderLayout.WEST);
        inputFileField = new JTextField();
        inputPanel.add(inputFileField, BorderLayout.CENTER);
        JButton browseInputButton = new JButton("Browse...");
        browseInputButton.addActionListener(e -> browseInputFile());
        inputPanel.add(browseInputButton, BorderLayout.EAST);
        panel.add(inputPanel);

        panel.add(Box.createVerticalStrut(5));

        // Output file
        JPanel outputPanel = new JPanel(new BorderLayout(5, 5));
        outputPanel.add(new JLabel("Output PSD:"), BorderLayout.WEST);
        outputFileField = new JTextField();
        outputPanel.add(outputFileField, BorderLayout.CENTER);
        JButton browseOutputButton = new JButton("Browse...");
        browseOutputButton.addActionListener(e -> browseOutputFile());
        outputPanel.add(browseOutputButton, BorderLayout.EAST);
        panel.add(outputPanel);

        return panel;
    }

    private JPanel createMetadataPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(new TitledBorder("File Information"));

        // Grid layout for metadata fields
        JPanel gridPanel = new JPanel(new GridLayout(2, 2, 10, 5));
        gridPanel.setBorder(new EmptyBorder(5, 5, 5, 5));

        // Dimensions
        gridPanel.add(new JLabel("Dimensions:"));
        metadataDimensionsLabel = new JLabel("—");
        metadataDimensionsLabel.setFont(new Font("Dialog", Font.BOLD, 11));
        gridPanel.add(metadataDimensionsLabel);

        // DPI
        gridPanel.add(new JLabel("DPI:"));
        metadataDpiLabel = new JLabel("—");
        metadataDpiLabel.setFont(new Font("Dialog", Font.BOLD, 11));
        gridPanel.add(metadataDpiLabel);

        panel.add(gridPanel);

        // Second row for layers and colors
        JPanel gridPanel2 = new JPanel(new GridLayout(1, 2, 10, 5));
        gridPanel2.setBorder(new EmptyBorder(0, 5, 5, 5));

        // Layers (will be detected from PSD)
        JPanel layersPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        layersPanel.add(new JLabel("Layers: "));
        metadataLayersLabel = new JLabel("—");
        metadataLayersLabel.setFont(new Font("Dialog", Font.BOLD, 11));
        layersPanel.add(metadataLayersLabel);
        gridPanel2.add(layersPanel);

        // Colors (will be detected when processing)
        JPanel colorsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        colorsPanel.add(new JLabel("Distinct Colors: "));
        metadataColorsLabel = new JLabel("—");
        metadataColorsLabel.setFont(new Font("Dialog", Font.BOLD, 11));
        colorsPanel.add(metadataColorsLabel);
        gridPanel2.add(colorsPanel);

        panel.add(gridPanel2);

        return panel;
    }

    private JPanel createModeSelectionPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(new TitledBorder("Trapping Mode"));

        JPanel innerPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        innerPanel.add(new JLabel("Mode:"));

        modeComboBox = new JComboBox<>(new String[]{"Offset Lithography", "Screen Printing"});
        modeComboBox.setToolTipText("<html><b>Offset Lithography:</b> High-precision commercial printing (0-1/32\" typical)<br>" +
                                    "<b>Screen Printing:</b> Garment printing, posters (0-6pt typical)</html>");
        modeComboBox.addActionListener(e -> updateDefaultsForMode());
        innerPanel.add(modeComboBox);

        // Info label
        JLabel infoLabel = new JLabel();
        infoLabel.setFont(new Font("Dialog", Font.ITALIC, 11));
        updateModeInfoLabel(infoLabel);
        modeComboBox.addActionListener(e -> updateModeInfoLabel(infoLabel));
        innerPanel.add(Box.createHorizontalStrut(20));
        innerPanel.add(infoLabel);

        panel.add(innerPanel, BorderLayout.WEST);

        return panel;
    }

    private void updateModeInfoLabel(JLabel label) {
        String mode = (String) modeComboBox.getSelectedItem();
        if (mode.equals("Offset Lithography")) {
            label.setText("Light expands under dark - Typical: 0-1/32\" @ 300 DPI");
        } else {
            label.setText("Light expands under dark - Typical: 0-6pt @ 300-600 DPI");
        }
    }

    private JPanel createTrapSizePanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(new TitledBorder("Trap Sizes"));

        JPanel fieldsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));

        fieldsPanel.add(new JLabel("Min Trap:"));
        minTrapField = new JTextField("0", 8);
        fieldsPanel.add(minTrapField);

        fieldsPanel.add(Box.createHorizontalStrut(20));

        fieldsPanel.add(new JLabel("Max Trap:"));
        maxTrapField = new JTextField("4", 8);
        fieldsPanel.add(maxTrapField);

        fieldsPanel.add(Box.createHorizontalStrut(10));

        fieldsPanel.add(new JLabel("Unit:"));
        unitComboBox = new JComboBox<>(new String[]{"points (pt)", "inches (\"))", "fraction (1/32)"});
        fieldsPanel.add(unitComboBox);

        panel.add(fieldsPanel);

        // Help text
        JLabel helpLabel = new JLabel("<html><i>Examples: \"4pt\", \"0.03125\", \"1/32\"</i></html>");
        helpLabel.setFont(new Font("Dialog", Font.PLAIN, 10));
        JPanel helpPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        helpPanel.add(helpLabel);
        panel.add(helpPanel);

        return panel;
    }

    private JPanel createProcessPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.CENTER));

        processButton = new JButton("Process File");
        processButton.setFont(new Font("Dialog", Font.BOLD, 14));
        processButton.setPreferredSize(new Dimension(200, 40));
        processButton.addActionListener(e -> processFile());
        panel.add(processButton);

        return panel;
    }

    private JPanel createLogPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(new TitledBorder("Log"));

        logArea = new JTextArea(10, 50);
        logArea.setEditable(false);
        logArea.setFont(new Font("Monospaced", Font.PLAIN, 11));
        JScrollPane scrollPane = new JScrollPane(logArea);
        panel.add(scrollPane, BorderLayout.CENTER);

        return panel;
    }

    private void loadFileMetadata(File file) {
        try {
            // Read PSD using ImageIO with TwelveMonkeys
            ImageInputStream iis = ImageIO.createImageInputStream(file);
            if (iis == null) {
                resetMetadata();
                metadataDimensionsLabel.setText("Cannot read file");
                return;
            }

            Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);

            if (!readers.hasNext()) {
                resetMetadata();
                metadataDimensionsLabel.setText("Not a valid PSD file");
                iis.close();
                return;
            }

            ImageReader reader = readers.next();
            reader.setInput(iis, false);

            // Get dimensions
            int width = reader.getWidth(0);
            int height = reader.getHeight(0);
            metadataDimensionsLabel.setText(width + " × " + height + " px");

            // Extract DPI from metadata using the same method as PsdColorSeparator
            int dpi = 72; // Default DPI if not found
            try {
                IIOMetadata metadata = reader.getImageMetadata(0);
                if (metadata != null) {
                    dpi = extractDPI(metadata);
                }
            } catch (Exception e) {
                // Metadata parsing failed, use default
            }

            metadataDpiLabel.setText(dpi + " DPI");

            // Count layers (if available from reader)
            try {
                int numImages = reader.getNumImages(true);
                if (numImages > 1) {
                    metadataLayersLabel.setText(String.valueOf(numImages));
                } else {
                    metadataLayersLabel.setText("1 (flattened)");
                }
            } catch (Exception e) {
                metadataLayersLabel.setText("Unknown");
            }

            // Colors will be determined during processing
            metadataColorsLabel.setText("(will detect during processing)");

            reader.dispose();
            iis.close();

        } catch (java.io.IOException e) {
            // If metadata reading fails, show specific error
            resetMetadata();
            if (e.getMessage() != null && e.getMessage().contains("permission")) {
                metadataDimensionsLabel.setText("Permission denied");
            } else {
                metadataDimensionsLabel.setText("Cannot read file");
            }
        } catch (Exception e) {
            // Generic error
            resetMetadata();
            metadataDimensionsLabel.setText("Error reading file");
        }
    }

    private void resetMetadata() {
        metadataDimensionsLabel.setText("—");
        metadataDpiLabel.setText("—");
        metadataLayersLabel.setText("—");
        metadataColorsLabel.setText("—");
    }

    /**
     * Extracts DPI from image metadata (same logic as PsdColorSeparator)
     */
    private int extractDPI(IIOMetadata metadata) {
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
     * Recursively searches for DPI information in metadata nodes (same logic as PsdColorSeparator)
     */
    private int searchDPIInNode(Node node) {
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
                        // Parse as double first to handle decimals like "300.0"
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

    private void browseInputFile() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("PSD Files", "psd"));

        if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            inputFileField.setText(file.getAbsolutePath());

            // Auto-generate output filename
            String outputPath = file.getAbsolutePath().replace(".psd", "-trapped.psd");
            outputFileField.setText(outputPath);

            // Load and display file metadata
            loadFileMetadata(file);
        }
    }

    private void browseOutputFile() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("PSD Files", "psd"));

        if (fileChooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            String path = file.getAbsolutePath();
            if (!path.toLowerCase().endsWith(".psd")) {
                path += ".psd";
            }
            outputFileField.setText(path);
        }
    }

    private void updateDefaultsForMode() {
        String mode = (String) modeComboBox.getSelectedItem();
        if (mode.equals("Offset Lithography")) {
            minTrapField.setText("0");
            maxTrapField.setText("1/32");
            unitComboBox.setSelectedIndex(2); // fraction
        } else {
            minTrapField.setText("0");
            maxTrapField.setText("4");
            unitComboBox.setSelectedIndex(0); // points
        }
    }

    private void processFile() {
        // Validate inputs
        String inputPath = inputFileField.getText().trim();
        String outputPath = outputFileField.getText().trim();

        if (inputPath.isEmpty()) {
            showError("Please select an input file");
            return;
        }

        if (outputPath.isEmpty()) {
            showError("Please select an output file");
            return;
        }

        File inputFile = new File(inputPath);
        if (!inputFile.exists()) {
            showError("Input file does not exist:\n" + inputPath);
            return;
        }

        // Validate input file is readable
        if (!inputFile.canRead()) {
            showError("Cannot read input file (permission denied):\n" + inputPath);
            return;
        }

        // Validate input file extension
        if (!inputPath.toLowerCase().endsWith(".psd")) {
            int result = JOptionPane.showConfirmDialog(this,
                "Input file does not have .psd extension.\n" +
                "File: " + inputFile.getName() + "\n\n" +
                "Continue anyway?",
                "Warning",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);
            if (result != JOptionPane.YES_OPTION) {
                return;
            }
        }

        // Validate input file size (warn if > 500 MB)
        long fileSizeMB = inputFile.length() / (1024 * 1024);
        if (fileSizeMB > 500) {
            int result = JOptionPane.showConfirmDialog(this,
                String.format("Input file is very large (%d MB).\n" +
                "Processing may take several minutes and require significant memory.\n\n" +
                "Continue anyway?", fileSizeMB),
                "Large File Warning",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);
            if (result != JOptionPane.YES_OPTION) {
                return;
            }
        }

        // Validate output directory exists and is writable
        File outputFile = new File(outputPath);
        File outputDir = outputFile.getParentFile();
        if (outputDir != null && !outputDir.exists()) {
            showError("Output directory does not exist:\n" + outputDir.getAbsolutePath());
            return;
        }
        if (outputDir != null && !outputDir.canWrite()) {
            showError("Cannot write to output directory (permission denied):\n" + outputDir.getAbsolutePath());
            return;
        }

        // Warn if output file already exists
        if (outputFile.exists()) {
            int result = JOptionPane.showConfirmDialog(this,
                "Output file already exists:\n" + outputFile.getName() + "\n\n" +
                "Overwrite?",
                "Confirm Overwrite",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);
            if (result != JOptionPane.YES_OPTION) {
                return;
            }
        }

        // Parse trap sizes
        String minTrapStr = minTrapField.getText().trim();
        String maxTrapStr = maxTrapField.getText().trim();
        String unit = (String) unitComboBox.getSelectedItem();

        // Add unit suffix if needed
        if (unit.startsWith("points")) {
            if (!minTrapStr.endsWith("pt") && !minTrapStr.contains("/")) {
                minTrapStr += "pt";
            }
            if (!maxTrapStr.endsWith("pt") && !maxTrapStr.contains("/")) {
                maxTrapStr += "pt";
            }
        }

        double minTrap;
        double maxTrap;

        try {
            minTrap = parseTrapSize(minTrapStr);
            maxTrap = parseTrapSize(maxTrapStr);
        } catch (Exception e) {
            showError("Invalid trap size: " + e.getMessage());
            return;
        }

        // Get strategy
        TrappingStrategy strategy;
        String mode = (String) modeComboBox.getSelectedItem();
        if (mode.equals("Offset Lithography")) {
            strategy = offsetStrategy;
        } else {
            strategy = screenStrategy;
        }

        // Process in background thread
        processButton.setEnabled(false);
        progressBar.setIndeterminate(true);
        progressBar.setString("Processing...");
        logArea.setText("");
        log("Starting processing...");
        log("Mode: " + mode);
        log("Input: " + inputPath);
        log("Output: " + outputPath);
        log("Trap range: " + minTrap + "\" to " + maxTrap + "\"");
        log("");

        final double finalMinTrap = minTrap;
        final double finalMaxTrap = maxTrap;

        SwingWorker<Void, String> worker = new SwingWorker<Void, String>() {
            @Override
            protected Void doInBackground() throws Exception {
                try {
                    PsdColorSeparator.processFile(inputPath, outputPath, finalMinTrap, finalMaxTrap, strategy);
                } catch (Exception e) {
                    throw e;
                }
                return null;
            }

            @Override
            protected void done() {
                try {
                    get(); // Check for exceptions
                    progressBar.setIndeterminate(false);
                    progressBar.setValue(100);
                    progressBar.setString("Complete!");
                    log("");
                    log("✓ Processing complete!");
                    log("Output saved to: " + outputPath);
                    JOptionPane.showMessageDialog(TrapperGUI.this,
                        "Processing complete!\n\nOutput: " + outputPath,
                        "Success",
                        JOptionPane.INFORMATION_MESSAGE);
                } catch (Exception e) {
                    progressBar.setIndeterminate(false);
                    progressBar.setValue(0);
                    progressBar.setString("Error");

                    // Extract root cause
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    String errorMessage = cause.getMessage();

                    log("");
                    log("✗ Error: " + errorMessage);

                    // Provide user-friendly error messages based on error type
                    String userMessage = formatErrorMessage(cause, errorMessage);
                    showError(userMessage);
                } finally {
                    processButton.setEnabled(true);
                }
            }
        };

        worker.execute();
    }

    private double parseTrapSize(String sizeSpec) throws IllegalArgumentException {
        sizeSpec = sizeSpec.trim();

        // Check for points: "2pt", "4pt", "6pt"
        if (sizeSpec.toLowerCase().endsWith("pt")) {
            String pointStr = sizeSpec.substring(0, sizeSpec.length() - 2).trim();
            try {
                double points = Double.parseDouble(pointStr);
                return points / 72.0; // 72 points = 1 inch
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid point value: " + pointStr);
            }
        }

        // Check for fraction: "1/32", "1/64", etc
        if (sizeSpec.contains("/")) {
            String[] parts = sizeSpec.split("/");
            if (parts.length != 2) {
                throw new IllegalArgumentException("Invalid fraction format: " + sizeSpec);
            }
            try {
                double numerator = Double.parseDouble(parts[0].trim());
                double denominator = Double.parseDouble(parts[1].trim());
                if (denominator == 0) {
                    throw new IllegalArgumentException("Division by zero in fraction");
                }
                return numerator / denominator;
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid fraction: " + sizeSpec);
            }
        }

        // Otherwise parse as decimal inches
        try {
            return Double.parseDouble(sizeSpec);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid decimal value: " + sizeSpec);
        }
    }

    private void log(String message) {
        logArea.append(message + "\n");
        logArea.setCaretPosition(logArea.getDocument().getLength());
    }

    /**
     * Format error messages to be user-friendly based on error type
     */
    private String formatErrorMessage(Throwable cause, String errorMessage) {
        // Out of memory errors
        if (cause instanceof OutOfMemoryError ||
            (errorMessage != null && errorMessage.toLowerCase().contains("out of memory"))) {
            return "Out of Memory Error\n\n" +
                   "The file is too large to process with available memory.\n\n" +
                   "Solutions:\n" +
                   "• Close other applications to free up memory\n" +
                   "• Use a smaller input file\n" +
                   "• Increase Java heap size with: java -Xmx4G -jar trapper.jar";
        }

        // Too many colors error
        if (errorMessage != null && errorMessage.contains("more than 10 distinct colors")) {
            return "Too Many Colors\n\n" +
                   "The image contains more than 10 distinct colors.\n" +
                   "Trapper currently supports a maximum of 10 colors.\n\n" +
                   "Solutions:\n" +
                   "• Reduce the number of colors in Photoshop\n" +
                   "• Use Image → Mode → Indexed Color to limit colors\n" +
                   "• Manually merge similar colors";
        }

        // File not found or I/O errors
        if (cause instanceof java.io.FileNotFoundException) {
            return "File Not Found\n\n" +
                   "Could not find the input file.\n" +
                   "It may have been moved or deleted.\n\n" +
                   "Error: " + errorMessage;
        }

        // Generic I/O errors
        if (cause instanceof java.io.IOException) {
            String msg = "File Read/Write Error\n\n";

            if (errorMessage != null) {
                if (errorMessage.toLowerCase().contains("no space left")) {
                    msg += "Disk is full - cannot write output file.\n\n" +
                           "Free up disk space and try again.";
                } else if (errorMessage.toLowerCase().contains("permission denied") ||
                           errorMessage.toLowerCase().contains("access denied")) {
                    msg += "Permission denied - cannot access file.\n\n" +
                           "Check file permissions and try again.";
                } else if (errorMessage.toLowerCase().contains("no reader found")) {
                    msg += "Invalid or corrupted PSD file.\n\n" +
                           "The file may not be a valid Photoshop PSD file,\n" +
                           "or it may be corrupted.\n\n" +
                           "Try opening it in Photoshop and saving it again.";
                } else {
                    msg += "Could not read or write file.\n\n" +
                           "Error: " + errorMessage;
                }
            }
            return msg;
        }

        // Invalid argument errors (trap sizes, etc)
        if (cause instanceof IllegalArgumentException) {
            return "Invalid Input\n\n" + errorMessage;
        }

        // Generic error with full message
        if (errorMessage != null && !errorMessage.isEmpty()) {
            return "Processing Error\n\n" + errorMessage;
        }

        // Unknown error
        return "An unexpected error occurred.\n\n" +
               "Error type: " + cause.getClass().getSimpleName() + "\n\n" +
               "Please check the log for details or report this issue on GitHub.";
    }

    private void showError(String message) {
        JOptionPane.showMessageDialog(this, message, "Error", JOptionPane.ERROR_MESSAGE);
    }

    public static void main(String[] args) {
        // Set system look and feel
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            // Fall back to default look and feel
        }

        SwingUtilities.invokeLater(() -> {
            TrapperGUI gui = new TrapperGUI();
            gui.setVisible(true);
        });
    }
}
