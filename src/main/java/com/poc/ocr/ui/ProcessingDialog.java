package com.poc.ocr.ui;

import com.poc.ocr.config.AppConfig;
import com.poc.ocr.model.DocumentProcessingResult;
import com.poc.ocr.model.ExtractionPayload;
import com.poc.ocr.service.DocumentInputPreparer;
import com.poc.ocr.service.GeminiOcrService;
import com.poc.ocr.service.OcrService;
import com.poc.ocr.service.OpenAIOcrService;
import com.poc.ocr.service.ResultWriter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;

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
import java.awt.GridLayout;
import java.awt.Image;
import java.awt.Font;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
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
    private static final String TARGET_COUNTRY_CODE = "CL";
    private static final Logger LOGGER = Logger.getLogger(ProcessingDialog.class.getName());
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    private final AppConfig config;
    private final List<DocumentInputPreparer.PreparedDocument> documents;
    private final List<DocumentProcessingResult> processedResults;
    private final OcrService ocrService;

    private final JLabel indexLabel;
    private final JLabel statusLabel;
    private final JLabel imageLabel;
    private final JLabel invoiceTagLabel;
    private final JLabel carTagLabel;
    private final JProgressBar progressBar;
    private final JTextArea proseTextArea;
    private final JTextArea jsonTextArea;
    private final JButton nextButton;
    private final JButton closeButton;

    private int currentIndex = 0;
    private boolean processingStarted = false;
    private SwingWorker<DocumentProcessingResult, Void> currentWorker;

    public ProcessingDialog(Frame owner, AppConfig config, List<DocumentInputPreparer.PreparedDocument> documents) {
        super(owner, "Procesamiento OCR", true);
        this.config = config;
        this.documents = documents;
        this.processedResults = new ArrayList<>();
        for (int i = 0; i < documents.size(); i++) {
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

        proseTextArea = new JTextArea(10, 40);
        proseTextArea.setEditable(false);
        proseTextArea.setLineWrap(true);
        proseTextArea.setWrapStyleWord(true);
        JScrollPane proseScroll = new JScrollPane(proseTextArea);

        jsonTextArea = new JTextArea(10, 40);
        jsonTextArea.setEditable(false);
        jsonTextArea.setLineWrap(false);
        jsonTextArea.setWrapStyleWord(false);
        jsonTextArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        JScrollPane jsonScroll = new JScrollPane(jsonTextArea);

        JPanel textPanel = new JPanel(new GridLayout(1, 2, 8, 0));
        textPanel.add(proseScroll);
        textPanel.add(jsonScroll);
        textPanel.setPreferredSize(new Dimension(850, 220));

        JPanel bottomPanel = new JPanel(new BorderLayout(8, 8));
        JPanel tagsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 16, 0));
        invoiceTagLabel = new JLabel();
        carTagLabel = new JLabel();
        resetClassificationLabels();
        tagsPanel.add(invoiceTagLabel);
        tagsPanel.add(carTagLabel);
        bottomPanel.add(tagsPanel, BorderLayout.NORTH);
        bottomPanel.add(textPanel, BorderLayout.CENTER);

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
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowOpened(WindowEvent e) {
                if (!processingStarted) {
                    processingStarted = true;
                    startProcessingCurrentImage();
                }
            }
        });
    }

    private void onNext() {
        if (currentWorker != null && !currentWorker.isDone()) {
            return;
        }
        if (currentIndex >= documents.size() - 1) {
            return;
        }
        currentIndex++;
        startProcessingCurrentImage();
    }

    private void startProcessingCurrentImage() {
        DocumentInputPreparer.PreparedDocument document = documents.get(currentIndex);
        indexLabel.setText("Documento " + (currentIndex + 1) + " de " + documents.size() + " - " + document.displayName());
        nextButton.setEnabled(false);
        proseTextArea.setText("");
        jsonTextArea.setText("");
        resetClassificationLabels();

        if (document.imagePath() != null) {
            setImagePreview(document.imagePath());
        } else {
            imageLabel.setIcon(null);
            imageLabel.setText("Documento sin imagen extraible");
        }
        setProgressSending();

        currentWorker = new SwingWorker<>() {
            @Override
            protected DocumentProcessingResult doInBackground() {
                try {
                    if (document.imagePath() == null) {
                        return DocumentProcessingResult.failure(
                                document.sourcePath(),
                                "local-pdf-check",
                                "pdf-text-only",
                                OffsetDateTime.now(),
                                document.suspicionReason() == null
                                        ? "PDF sin imagenes extraibles; sospechoso."
                                        : document.suspicionReason()
                        );
                    }

                    ExtractionPayload extraction = ocrService.analyzeImage(document.imagePath());
                    return DocumentProcessingResult.success(
                            document.sourcePath(),
                            ocrService.providerName(),
                            ocrService.modelName(),
                            OffsetDateTime.now(),
                            extraction
                    );
                } catch (Exception e) {
                    return DocumentProcessingResult.failure(
                            document.sourcePath(),
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
                    DisplayContent displayContent = buildDisplayContent(result);
                    proseTextArea.setText(displayContent.prose());
                    jsonTextArea.setText(displayContent.json());
                    statusLabel.setText(result.success() ? "Completado" : "Error en OCR/API");

                    persistCurrentResults();
                } catch (Exception e) {
                    proseTextArea.setText("Error inesperado al finalizar: " + e.getMessage());
                    jsonTextArea.setText("{\"error\":\"Error inesperado al finalizar\"}");
                    statusLabel.setText("Error inesperado");
                }

                boolean hasPending = currentIndex < documents.size() - 1;
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

    private DisplayContent buildDisplayContent(DocumentProcessingResult result) {
        String prose;
        if (!result.success()) {
            String json = buildResultJson(result, null);
            String error = result.error() == null ? "" : result.error();
            if (normalize(error).contains("sospechoso")) {
                prose = "Documento sospechoso: " + error;
            } else {
                prose = "Error durante OCR/API: " + error;
            }
            return new DisplayContent(prose, json);
        }

        ExtractionPayload extraction = result.extraction();
        if (extraction == null) {
            String json = buildResultJson(result, null);
            prose = "No se obtuvo estructura de extraccion.";
            return new DisplayContent(prose, json);
        }
        if (extraction.rawText() != null && !extraction.rawText().isBlank()) {
            LOGGER.info("raw_text OCR: " + extraction.rawText());
        } else {
            LOGGER.info("raw_text OCR no disponible para esta imagen.");
        }
        Evaluation evaluation = evaluateExtraction(extraction);
        String json = buildResultJson(result, evaluation);
        if (!evaluation.isUsefulInvoice()) {
            prose = buildNotUsefulInterpretation(extraction, evaluation);
        } else {
            prose = buildInvoiceInterpretation(extraction, evaluation);
        }
        return new DisplayContent(prose, json);
    }

    private static String buildResultJson(DocumentProcessingResult result, Evaluation evaluation) {
        try {
            if (result.success() && result.extraction() != null) {
                ObjectNode node = JSON_MAPPER.valueToTree(result.extraction());
                if (evaluation != null) {
                    node.put("is_invoice", evaluation.isInvoice());
                    node.put("is_chile_invoice", evaluation.isChileInvoice());
                    node.put("is_vehicle_invoice", evaluation.isVehicleInvoice());
                    node.put("is_useful_invoice", evaluation.isUsefulInvoice());
                    String normalizedPlate = normalizedVehiclePlate(result.extraction());
                    if (normalizedPlate != null) {
                        node.put("vehicle_plate", normalizedPlate);
                    }
                    if (evaluation.countryCode() != null) {
                        node.put("country_code", evaluation.countryCode());
                    }
                }
                return JSON_MAPPER.writeValueAsString(node);
            }
            ObjectNode errorNode = JSON_MAPPER.createObjectNode();
            errorNode.put("is_invoice", false);
            errorNode.put("is_chile_invoice", false);
            errorNode.put("is_vehicle_invoice", false);
            errorNode.put("is_useful_invoice", false);
            errorNode.put("file_name", result.fileName());
            errorNode.put("file_path", result.filePath());
            errorNode.put("error", coalesce("Error no especificado", result.error()));
            return JSON_MAPPER.writeValueAsString(errorNode);
        } catch (Exception e) {
            return "{\"error\":\"No se pudo serializar resultado a JSON: " + sanitizeJson(e.getMessage()) + "\"}";
        }
    }

    private static String sanitizeJson(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String buildInvoiceInterpretation(ExtractionPayload extraction, Evaluation evaluation) {
        String supplier = coalesce("comercio no identificado", extraction.merchantName(), extraction.supplierName());
        String customer = coalesce("cliente no identificado", extraction.customerName());
        String date = coalesce("fecha no identificada", extraction.purchaseDate(), extraction.invoiceDate());
        String number = coalesce("sin numero visible", extraction.invoiceNumber());
        String total = formatAmount(
                firstNonNull(extraction.totalPurchase(), extraction.total()),
                extraction.currency(),
                "monto total no identificado"
        );
        String subtotal = formatAmount(extraction.subtotal(), extraction.currency(), null);
        String tax = formatAmount(extraction.tax(), extraction.currency(), null);
        String taxId = coalesce(null, extraction.supplierTaxId());
        boolean vehicleInvoice = evaluation.isVehicleInvoice();
        String vehicleCondition = coalesce(null, extraction.vehicleCondition(), extraction.vehicleConditionDetail());
        String vehicleMake = coalesce(null, extraction.vehicleBrand(), extraction.vehicleMake());
        String vehicleModel = coalesce(null, extraction.vehicleModel());
        String vehicleYear = coalesce(null, extraction.vehicleYear());
        String commercialYear = coalesce(null, extraction.commercialYear());
        String vehicleType = coalesce(null, extraction.vehicleType());
        String vehicleColor = coalesce(null, extraction.vehicleColor());
        String vin = coalesce(null, extraction.vehicleVin());
        String plate = normalizedVehiclePlate(extraction);
        String engineNumber = coalesce(null, extraction.engineNumber(), extraction.vehicleEngineNumber());
        String chassisNumber = coalesce(null, extraction.vehicleChassisNumber());
        String mileage = coalesce(null, extraction.vehicleMileage());
        String fuelType = coalesce(null, extraction.fuelType());
        String sellerName = coalesce(null, extraction.sellerName());
        String merchantCity = coalesce(null, extraction.merchantCity());
        String confidence = formatProbability(extraction.invoiceProbability(), extraction.confidence());
        String chileConfidence = formatProbability(extraction.chileInvoiceProbability(), null);
        String vehicleConfidence = formatProbability(extraction.vehicleInvoiceProbability(), null);

        StringBuilder prose = new StringBuilder();
        prose.append(vehicleInvoice
                        ? "Es una factura de compra/venta de vehiculo emitida por "
                        : "Es una factura emitida por ")
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
        if (merchantCity != null) {
            prose.append(" Ciudad del comercio: ").append(merchantCity).append(".");
        }
        if (sellerName != null) {
            prose.append(" Vendedor identificado: ").append(sellerName).append(".");
        }
        if (vehicleInvoice) {
            prose.append(" Datos de vehiculo: ");
            List<String> details = new ArrayList<>();
            if (vehicleCondition != null) {
                details.add("condicion " + vehicleCondition);
            }
            if (vehicleMake != null) {
                details.add("marca " + vehicleMake);
            }
            if (vehicleModel != null) {
                details.add("modelo " + vehicleModel);
            }
            if (vehicleYear != null) {
                details.add("anio " + vehicleYear);
            }
            if (commercialYear != null) {
                details.add("anio comercial " + commercialYear);
            }
            if (vehicleType != null) {
                details.add("tipo " + vehicleType);
            }
            if (vehicleColor != null) {
                details.add("color " + vehicleColor);
            }
            if (vin != null) {
                details.add("VIN " + vin);
            }
            if (plate != null) {
                details.add("placa " + plate);
            }
            if (engineNumber != null) {
                details.add("motor " + engineNumber);
            }
            if (chassisNumber != null) {
                details.add("chasis " + chassisNumber);
            }
            if (mileage != null) {
                details.add("kilometraje " + mileage);
            }
            if (fuelType != null) {
                details.add("combustible " + fuelType);
            }
            if (details.isEmpty()) {
                prose.append("no se pudieron extraer campos vehiculares detallados.");
            } else {
                prose.append(String.join(", ", details)).append(".");
            }
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
            prose.append(" Probabilidad de ser factura: ").append(confidence).append(".");
        }
        if (chileConfidence != null) {
            prose.append(" Probabilidad de ser factura de Chile: ").append(chileConfidence).append(".");
        }
        if (vehicleConfidence != null) {
            prose.append(" Probabilidad de ser factura de vehiculo: ").append(vehicleConfidence).append(".");
        }
        prose.append(" Campos clave: cliente ")
                .append(customer)
                .append(", fecha ")
                .append(date)
                .append(", marca ")
                .append(coalesce("no identificada", vehicleMake))
                .append(", modelo ")
                .append(coalesce("no identificado", vehicleModel))
                .append(", anio ")
                .append(coalesce("no identificado", vehicleYear))
                .append(", VIN/BIN ")
                .append(coalesce("no identificado", vin))
                .append(", motor ")
                .append(coalesce("no identificado", engineNumber))
                .append(", chasis ")
                .append(coalesce("no identificado", chassisNumber))
                .append(", placa ")
                .append(coalesce("no identificada", plate))
                .append(".");
        if (extraction.notes() != null && !extraction.notes().isBlank()) {
            prose.append(" Nota: ").append(extraction.notes().trim()).append(".");
        }
        return prose.toString();
    }

    private static String buildNotUsefulInterpretation(ExtractionPayload extraction, Evaluation evaluation) {
        String docType = coalesce("documento no clasificado", extraction.documentType());
        String supplier = coalesce(null, extraction.merchantName(), extraction.supplierName());
        String date = coalesce(null, extraction.purchaseDate(), extraction.invoiceDate());
        String number = coalesce(null, extraction.invoiceNumber());
        String invoiceConfidence = formatProbability(extraction.invoiceProbability(), extraction.confidence());
        String chileConfidence = formatProbability(extraction.chileInvoiceProbability(), null);
        String vehicleConfidence = formatProbability(extraction.vehicleInvoiceProbability(), null);

        StringBuilder prose = new StringBuilder();
        prose.append("No se detecto una factura util para este flujo (factura + Chile + vehiculo). ")
                .append("Se interpreta como un documento tipo ")
                .append(docType)
                .append(".");

        List<String> blockers = new ArrayList<>();
        if (!evaluation.isInvoice()) {
            blockers.add("no cumple condicion de factura");
        }
        if (!evaluation.isChileInvoice()) {
            blockers.add("no se detecta como documento de Chile");
        }
        if (!evaluation.isVehicleInvoice()) {
            blockers.add("no se detecta como factura de compra/venta de vehiculo");
        }
        if (!blockers.isEmpty()) {
            prose.append(" Motivo: ").append(String.join("; ", blockers)).append(".");
        }

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
        if (invoiceConfidence != null) {
            prose.append(" Probabilidad de ser factura: ").append(invoiceConfidence).append(".");
        }
        if (chileConfidence != null) {
            prose.append(" Probabilidad de ser factura de Chile: ").append(chileConfidence).append(".");
        }
        if (vehicleConfidence != null) {
            prose.append(" Probabilidad de ser factura de vehiculo: ").append(vehicleConfidence).append(".");
        }
        return prose.toString();
    }

    private static String coalesce(String fallback, String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return fallback;
    }

    private static Object firstNonNull(Object... values) {
        for (Object value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static String formatProbability(Double primary, Double secondary) {
        Double selected = primary != null ? primary : secondary;
        if (selected == null) {
            return null;
        }
        return String.format("%.2f", selected);
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

    private static String normalizedVehiclePlate(ExtractionPayload extraction) {
        String explicitPlate = coalesce(null, extraction.vehiclePlate());
        if (explicitPlate != null) {
            return explicitPlate;
        }
        if (isNewVehicleCondition(extraction)) {
            return "SIN_PLACA";
        }
        return null;
    }

    private static boolean isNewVehicleCondition(ExtractionPayload extraction) {
        String condition = coalesce(null, extraction.vehicleCondition(), extraction.vehicleConditionDetail());
        if (condition == null) {
            return false;
        }
        String normalized = normalize(condition);
        return normalized.contains("new")
                || normalized.contains("nuevo")
                || normalized.contains("0km")
                || normalized.contains("0 km");
    }

    private static Evaluation evaluateExtraction(ExtractionPayload extraction) {
        boolean isInvoice = extraction.isInvoice();
        boolean isChile = isInvoice && detectChileInvoice(extraction);
        boolean isVehicle = isInvoice && detectCarInvoice(extraction);
        boolean isUseful = isInvoice && isChile && isVehicle;
        String country = coalesce(null, extraction.countryCode(), isChile ? TARGET_COUNTRY_CODE : null);
        return new Evaluation(isInvoice, isChile, isVehicle, isUseful, country);
    }

    private void resetClassificationLabels() {
        invoiceTagLabel.setText(asHtmlTag("ES FACTURA", "-", "#666666", false));
        carTagLabel.setText(asHtmlTag("ES DE CARRO", "-", "#666666", false));
    }

    private void updateClassificationLabels(DocumentProcessingResult result) {
        boolean isInvoice = false;
        boolean isCarInvoice = false;

        if (result.success() && result.extraction() != null) {
            Evaluation evaluation = evaluateExtraction(result.extraction());
            isInvoice = evaluation.isInvoice();
            isCarInvoice = evaluation.isVehicleInvoice();
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
        if (extraction.isVehicleInvoice() != null) {
            return extraction.isVehicleInvoice();
        }

        StringBuilder source = new StringBuilder();
        appendIfPresent(source, extraction.rawText());
        appendIfPresent(source, extraction.documentType());
        appendIfPresent(source, extraction.notes());
        appendIfPresent(source, extraction.merchantName());
        appendIfPresent(source, extraction.supplierName());
        appendIfPresent(source, extraction.merchantCity());
        appendIfPresent(source, extraction.sellerName());
        appendIfPresent(source, extraction.customerName());
        appendIfPresent(source, extraction.vehicleCondition());
        appendIfPresent(source, extraction.vehicleConditionDetail());
        appendIfPresent(source, extraction.vehicleBrand());
        appendIfPresent(source, extraction.vehicleMake());
        appendIfPresent(source, extraction.vehicleModel());
        appendIfPresent(source, extraction.vehicleYear());
        appendIfPresent(source, extraction.commercialYear());
        appendIfPresent(source, extraction.vehicleType());
        appendIfPresent(source, extraction.vehicleColor());
        appendIfPresent(source, extraction.vehicleVin());
        appendIfPresent(source, extraction.vehiclePlate());
        appendIfPresent(source, extraction.engineNumber());
        appendIfPresent(source, extraction.vehicleEngineNumber());
        appendIfPresent(source, extraction.vehicleChassisNumber());
        appendIfPresent(source, extraction.vehicleMileage());
        appendIfPresent(source, extraction.fuelType());
        if (extraction.lineItems() != null) {
            extraction.lineItems().forEach(item -> appendIfPresent(source, item.description()));
        }

        String normalized = normalize(source.toString());
        List<String> negativeKeywords = List.of(
                "jamon", "queso", "arroz", "frijol", "pollo", "carne", "supermercado",
                "abarrotes", "restaurante", "farmacia", "bebida", "pan", "huevos", "lacteos"
        );
        if (containsAny(normalized, negativeKeywords)) {
            return false;
        }

        List<String> strongVehicleKeywords = List.of(
                "vin", "chasis", "numero de motor", "motor", "placa", "vehiculo", "automovil",
                "carro", "camioneta", "pickup", "sedan", "suv", "concesionario", "odometro", "kilometraje"
        );
        List<String> veryStrongIndicators = List.of("vin", "chasis", "numero de motor", "placa");
        if (extraction.isInvoice() && containsAny(normalized, veryStrongIndicators)) {
            return true;
        }
        int hits = 0;
        for (String keyword : strongVehicleKeywords) {
            if (normalized.contains(keyword)) {
                hits++;
            }
        }
        return extraction.isInvoice() && hits >= 2;
    }

    private static boolean detectChileInvoice(ExtractionPayload extraction) {
        if (extraction.isChileInvoice() != null) {
            return extraction.isChileInvoice();
        }
        String countryCode = coalesce(null, extraction.countryCode());
        if (countryCode != null && countryCode.equalsIgnoreCase(TARGET_COUNTRY_CODE)) {
            return true;
        }

        StringBuilder source = new StringBuilder();
        appendIfPresent(source, extraction.rawText());
        appendIfPresent(source, extraction.notes());
        appendIfPresent(source, extraction.merchantCity());
        appendIfPresent(source, extraction.merchantName());
        appendIfPresent(source, extraction.supplierName());
        appendIfPresent(source, extraction.customerName());
        appendIfPresent(source, extraction.currency());
        appendIfPresent(source, extraction.supplierTaxId());
        String normalized = normalize(source.toString());

        List<String> chileKeywords = List.of(
                "chile", "santiago", "valparaiso", "concepcion", "temuco",
                "puerto montt", "vina del mar", "rut", "clp", "region metropolitana"
        );
        return containsAny(normalized, chileKeywords);
    }

    private static boolean containsAny(String text, List<String> terms) {
        for (String term : terms) {
            if (text.contains(term)) {
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

    private record DisplayContent(String prose, String json) {
    }

    private record Evaluation(
            boolean isInvoice,
            boolean isChileInvoice,
            boolean isVehicleInvoice,
            boolean isUsefulInvoice,
            String countryCode
    ) {
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
            case "gemini" -> new GeminiOcrService(config.geminiApiKey(), config.geminiModel(), config.geminiApiUrl());
            case "openai" -> new OpenAIOcrService(
                    config.openAiApiKey(),
                    config.openAiModel(),
                    config.openAiApiUrl(),
                    "openai"
            );
            case "banco" -> new OpenAIOcrService(
                    config.bancoApiKey(),
                    config.bancoModel(),
                    config.bancoApiUrl(),
                    "banco"
            );
            default -> throw new IllegalArgumentException("Proveedor no soportado: " + config.provider());
        };
    }
}
