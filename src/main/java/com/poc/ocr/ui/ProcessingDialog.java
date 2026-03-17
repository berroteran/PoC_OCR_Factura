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
import java.text.Normalizer;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public final class ProcessingDialog extends JDialog {
    private static final Logger LOGGER = Logger.getLogger(ProcessingDialog.class.getName());

    private final AppConfig config;
    private final List<Path> imagePaths;
    private final List<DocumentProcessingResult> processedResults;
    private final OcrService ocrService;

    private final JLabel indexLabel;
    private final JLabel statusLabel;
    private final JLabel imageLabel;
    private final JLabel invoiceTagLabel;
    private final JLabel carTagLabel;
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
        JPanel tagsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 16, 0));
        invoiceTagLabel = new JLabel();
        carTagLabel = new JLabel();
        resetClassificationLabels();
        tagsPanel.add(invoiceTagLabel);
        tagsPanel.add(carTagLabel);
        bottomPanel.add(tagsPanel, BorderLayout.NORTH);
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
        resetClassificationLabels();

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
                    updateClassificationLabels(result);
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
        if (extraction.rawText() != null && !extraction.rawText().isBlank()) {
            LOGGER.info("raw_text OCR: " + extraction.rawText());
        } else {
            LOGGER.info("raw_text OCR no disponible para esta imagen.");
        }
        if (extraction.isInvoice()) {
            return buildInvoiceInterpretation(extraction);
        }
        return buildNonInvoiceInterpretation(extraction);
    }

    private static String buildInvoiceInterpretation(ExtractionPayload extraction) {
        String supplier = pick(extraction.supplierName(), "comercio no identificado");
        String customer = pick(extraction.customerName(), "cliente no identificado");
        String date = pick(extraction.invoiceDate(), "fecha no identificada");
        String number = pick(extraction.invoiceNumber(), "sin numero visible");
        String total = formatAmount(extraction.total(), extraction.currency(), "monto total no identificado");
        String subtotal = formatAmount(extraction.subtotal(), extraction.currency(), null);
        String tax = formatAmount(extraction.tax(), extraction.currency(), null);
        String taxId = pick(extraction.supplierTaxId(), null);
        String confidence = extraction.confidence() == null
                ? null
                : String.format("%.2f", extraction.confidence());

        StringBuilder prose = new StringBuilder();
        prose.append("Es una factura emitida por ")
                .append(supplier)
                .append(" para ")
                .append(customer)
                .append(", con fecha ")
                .append(date)
                .append(" y numero ")
                .append(number)
                .append(", por un monto de ")
                .append(total)
                .append(".");

        if (taxId != null) {
            prose.append(" El NIT/ID fiscal del comercio es ").append(taxId).append(".");
        }
        if (subtotal != null || tax != null) {
            prose.append(" Desglose: ");
            if (subtotal != null) {
                prose.append("subtotal ").append(subtotal);
            }
            if (subtotal != null && tax != null) {
                prose.append(", ");
            }
            if (tax != null) {
                prose.append("impuesto ").append(tax);
            }
            prose.append(".");
        }
        if (extraction.lineItems() != null && !extraction.lineItems().isEmpty()) {
            List<String> descriptions = extraction.lineItems().stream()
                    .map(item -> item.description())
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .filter(text -> !text.isBlank())
                    .limit(3)
                    .collect(Collectors.toList());
            if (!descriptions.isEmpty()) {
                prose.append(" Productos/servicios detectados: ")
                        .append(String.join(", ", descriptions))
                        .append(".");
            }
        }
        if (confidence != null) {
            prose.append(" Confianza estimada de extraccion: ").append(confidence).append(".");
        }
        if (extraction.notes() != null && !extraction.notes().isBlank()) {
            prose.append(" Nota: ").append(extraction.notes().trim()).append(".");
        }
        return prose.toString();
    }

    private static String buildNonInvoiceInterpretation(ExtractionPayload extraction) {
        String docType = pick(extraction.documentType(), "documento no clasificado");
        String supplier = pick(extraction.supplierName(), null);
        String date = pick(extraction.invoiceDate(), null);
        String number = pick(extraction.invoiceNumber(), null);
        String confidence = extraction.confidence() == null
                ? null
                : String.format("%.2f", extraction.confidence());

        StringBuilder prose = new StringBuilder();
        prose.append("No parece ser una factura. ")
                .append("Se interpreta como un documento tipo ")
                .append(docType)
                .append(".");

        if (supplier != null) {
            prose.append(" Entidad/comercio detectado: ").append(supplier).append(".");
        }
        if (date != null) {
            prose.append(" Fecha detectada: ").append(date).append(".");
        }
        if (number != null) {
            prose.append(" Numero de referencia detectado: ").append(number).append(".");
        }
        if (extraction.notes() != null && !extraction.notes().isBlank()) {
            prose.append(" Observacion del modelo: ").append(extraction.notes().trim()).append(".");
        }
        if (confidence != null) {
            prose.append(" Confianza estimada de clasificacion: ").append(confidence).append(".");
        }
        return prose.toString();
    }

    private static String pick(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim();
    }

    private static String formatAmount(Object amount, String currency, String fallback) {
        if (amount == null) {
            return fallback;
        }
        String value = amount.toString();
        if (currency == null || currency.isBlank()) {
            return value;
        }
        return currency.trim().toUpperCase() + " " + value;
    }

    private void resetClassificationLabels() {
        invoiceTagLabel.setText(asHtmlTag("ES FACTURA", "-", "#666666", false));
        carTagLabel.setText(asHtmlTag("ES DE CARRO", "-", "#666666", false));
    }

    private void updateClassificationLabels(DocumentProcessingResult result) {
        boolean isInvoice = false;
        boolean isCarInvoice = false;

        if (result.success() && result.extraction() != null) {
            ExtractionPayload extraction = result.extraction();
            isInvoice = extraction.isInvoice();
            isCarInvoice = isInvoice && detectCarInvoice(extraction);
        }

        invoiceTagLabel.setText(
                isInvoice
                        ? asHtmlTag("ES FACTURA", "SI", "#0b5ed7", true)
                        : asHtmlTag("ES FACTURA", "NO", "#d00000", false)
        );
        carTagLabel.setText(
                isCarInvoice
                        ? asHtmlTag("ES DE CARRO", "SI", "#0b5ed7", true)
                        : asHtmlTag("ES DE CARRO", "NO", "#d00000", false)
        );
    }

    private static boolean detectCarInvoice(ExtractionPayload extraction) {
        StringBuilder source = new StringBuilder();
        appendIfPresent(source, extraction.rawText());
        appendIfPresent(source, extraction.documentType());
        appendIfPresent(source, extraction.notes());
        appendIfPresent(source, extraction.supplierName());
        appendIfPresent(source, extraction.customerName());
        if (extraction.lineItems() != null) {
            extraction.lineItems().forEach(item -> appendIfPresent(source, item.description()));
        }

        String normalized = normalize(source.toString());
        List<String> keywords = List.of(
                "carro", "vehiculo", "automovil", "auto", "pickup", "camioneta",
                "sedan", "suv", "vin", "chasis", "placa", "motor", "modelo", "marca", "vehicle", "car"
        );
        for (String keyword : keywords) {
            if (normalized.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private static String normalize(String value) {
        String cleaned = value == null ? "" : value;
        String normalized = Normalizer.normalize(cleaned, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
        return normalized.toLowerCase();
    }

    private static void appendIfPresent(StringBuilder sb, String value) {
        if (value != null && !value.isBlank()) {
            sb.append(' ').append(value);
        }
    }

    private static String asHtmlTag(String label, String value, String color, boolean boldValue) {
        String valueHtml = boldValue
                ? "<b><span style='color:" + color + ";'>" + value + "</span></b>"
                : "<span style='color:" + color + ";'>" + value + "</span>";
        return "<html><b>" + label + ":</b> " + valueHtml + "</html>";
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
