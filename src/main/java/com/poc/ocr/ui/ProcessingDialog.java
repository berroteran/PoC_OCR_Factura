package com.poc.ocr.ui;

import com.poc.ocr.config.AppConfig;
import com.poc.ocr.model.DocumentProcessingResult;
import com.poc.ocr.model.ExtractionPayload;
import com.poc.ocr.service.GeminiOcrService;
import com.poc.ocr.service.OcrService;
import com.poc.ocr.service.OpenAIOcrService;
import com.poc.ocr.service.ResultWriter;

import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingWorker;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public final class ProcessingDialog extends JDialog {
    private final AppConfig config;
    private final List<Path> imagePaths;
    private final List<DocumentProcessingResult> processedResults;
    private final OcrService ocrService;

    private final JLabel indexLabel;
    private final JLabel statusLabel;
    private final JLabel imageLabel;
    private final JProgressBar progressBar;
    private final JTextArea recoveredTextArea;
    private final JButton nextButton;
    private final JButton closeButton;

    private int currentIndex = 0;
    private SwingWorker<DocumentProcessingResult, Void> currentWorker;

    public ProcessingDialog(Frame owner, AppConfig config, List<Path> imagePaths) {
        super(owner, "Procesamiento OCR", true);
        this.config = config;
        this.imagePaths = imagePaths;
        this.processedResults = new ArrayList<>();
        for (int i = 0; i < imagePaths.size(); i++) {
            processedResults.add(null);
        }
        this.ocrService = buildService(config);

        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout(10, 10));

        JPanel topPanel = new JPanel(new BorderLayout(8, 8));
        indexLabel = new JLabel();
        statusLabel = new JLabel("Listo para procesar");
        progressBar = new JProgressBar();
        progressBar.setStringPainted(true);
        progressBar.setValue(0);
        topPanel.add(indexLabel, BorderLayout.WEST);
        topPanel.add(statusLabel, BorderLayout.CENTER);
        topPanel.add(progressBar, BorderLayout.SOUTH);
        add(topPanel, BorderLayout.NORTH);

        imageLabel = new JLabel("Sin imagen", JLabel.CENTER);
        JScrollPane imageScroll = new JScrollPane(imageLabel);
        imageScroll.setPreferredSize(new Dimension(850, 450));
        add(imageScroll, BorderLayout.CENTER);

        recoveredTextArea = new JTextArea(10, 80);
        recoveredTextArea.setEditable(false);
        recoveredTextArea.setLineWrap(true);
        recoveredTextArea.setWrapStyleWord(true);
        JScrollPane textScroll = new JScrollPane(recoveredTextArea);
        textScroll.setPreferredSize(new Dimension(850, 200));

        JPanel bottomPanel = new JPanel(new BorderLayout(8, 8));
        bottomPanel.add(textScroll, BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        nextButton = new JButton("Siguiente");
        nextButton.setEnabled(false);
        nextButton.addActionListener(e -> onNext());
        closeButton = new JButton("Cerrar");
        closeButton.addActionListener(e -> dispose());
        actions.add(nextButton);
        actions.add(closeButton);
        bottomPanel.add(actions, BorderLayout.SOUTH);
        add(bottomPanel, BorderLayout.SOUTH);

        setSize(980, 860);
        setLocationRelativeTo(owner);

        startProcessingCurrentImage();
    }

    private void onNext() {
        if (currentWorker != null && !currentWorker.isDone()) {
            return;
        }
        if (currentIndex >= imagePaths.size() - 1) {
            return;
        }
        currentIndex++;
        startProcessingCurrentImage();
    }

    private void startProcessingCurrentImage() {
        Path imagePath = imagePaths.get(currentIndex);
        indexLabel.setText("Imagen " + (currentIndex + 1) + " de " + imagePaths.size() + " - " + imagePath.getFileName());
        nextButton.setEnabled(false);
        recoveredTextArea.setText("");

        setImagePreview(imagePath);
        setProgressSending();

        currentWorker = new SwingWorker<>() {
            @Override
            protected DocumentProcessingResult doInBackground() {
                try {
                    ExtractionPayload extraction = ocrService.analyzeImage(imagePath);
                    return DocumentProcessingResult.success(
                            imagePath,
                            ocrService.providerName(),
                            ocrService.modelName(),
                            OffsetDateTime.now(),
                            extraction
                    );
                } catch (Exception e) {
                    return DocumentProcessingResult.failure(
                            imagePath,
                            ocrService.providerName(),
                            ocrService.modelName(),
                            OffsetDateTime.now(),
                            e.getMessage()
                    );
                }
            }

            @Override
            protected void done() {
                try {
                    progressBar.setIndeterminate(false);
                    progressBar.setValue(100);
                    progressBar.setString("Informacion recuperada");

                    DocumentProcessingResult result = get();
                    processedResults.set(currentIndex, result);
                    recoveredTextArea.setText(buildRecoveredText(result));
                    statusLabel.setText(result.success() ? "Completado" : "Error en OCR/API");

                    persistCurrentResults();
                } catch (Exception e) {
                    recoveredTextArea.setText("Error inesperado al finalizar: " + e.getMessage());
                    statusLabel.setText("Error inesperado");
                }

                boolean hasPending = currentIndex < imagePaths.size() - 1;
                nextButton.setEnabled(hasPending);
            }
        };
        currentWorker.execute();
    }

    private void setProgressSending() {
        progressBar.setIndeterminate(true);
        progressBar.setString("Enviando a la API y recuperando informacion...");
        statusLabel.setText("Procesando...");
    }

    private void setImagePreview(Path imagePath) {
        try {
            ImageIcon icon = loadScaledIcon(imagePath, 820, 430);
            if (icon != null) {
                imageLabel.setText("");
                imageLabel.setIcon(icon);
            } else {
                imageLabel.setIcon(null);
                imageLabel.setText("No se pudo renderizar la imagen: " + imagePath.getFileName());
            }
        } catch (Exception e) {
            imageLabel.setIcon(null);
            imageLabel.setText("Error cargando imagen: " + e.getMessage());
        }
    }

    private static ImageIcon loadScaledIcon(Path imagePath, int maxWidth, int maxHeight) throws Exception {
        BufferedImage bufferedImage = ImageIO.read(imagePath.toFile());
        if (bufferedImage == null) {
            return null;
        }

        int width = bufferedImage.getWidth();
        int height = bufferedImage.getHeight();
        double ratio = Math.min((double) maxWidth / width, (double) maxHeight / height);
        ratio = Math.min(1.0, ratio);

        int scaledWidth = Math.max(1, (int) Math.round(width * ratio));
        int scaledHeight = Math.max(1, (int) Math.round(height * ratio));

        Image scaled = bufferedImage.getScaledInstance(scaledWidth, scaledHeight, Image.SCALE_SMOOTH);
        return new ImageIcon(scaled);
    }

    private String buildRecoveredText(DocumentProcessingResult result) {
        if (!result.success()) {
            return "Error:\n" + result.error();
        }
        ExtractionPayload extraction = result.extraction();
        if (extraction == null) {
            return "No se obtuvo estructura de extraccion.";
        }
        String rawText = extraction.rawText();
        if (rawText == null || rawText.isBlank()) {
            return "OCR completado, pero el modelo no devolvio raw_text.";
        }
        return rawText;
    }

    private void persistCurrentResults() {
        List<DocumentProcessingResult> finished = processedResults.stream()
                .filter(item -> item != null)
                .collect(Collectors.toList());
        try {
            ResultWriter.write(config.outputFile(), finished);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(
                    this,
                    "No se pudo guardar archivo de salida: " + e.getMessage(),
                    "Advertencia",
                    JOptionPane.WARNING_MESSAGE
            );
        }
    }

    private static OcrService buildService(AppConfig config) {
        return switch (config.provider().toLowerCase()) {
            case "gemini" -> new GeminiOcrService(config.geminiApiKey(), config.geminiModel());
            case "openai" -> new OpenAIOcrService(config.openAiApiKey(), config.openAiModel());
            default -> throw new IllegalArgumentException("Proveedor no soportado: " + config.provider());
        };
    }
}

