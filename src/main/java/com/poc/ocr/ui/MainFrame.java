package com.poc.ocr.ui;

import com.poc.ocr.config.AppConfig;
import com.poc.ocr.service.DocumentInputPreparer;
import com.poc.ocr.service.ImageScanner;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

public final class MainFrame extends JFrame {
    private final Consumer<AppConfig> onConfigSaved;
    private final JLabel configLabel;

    private AppConfig currentConfig;

    public MainFrame(AppConfig initialConfig, Consumer<AppConfig> onConfigSaved) {
        super("PoC OCR Factura");
        this.currentConfig = initialConfig;
        this.onConfigSaved = onConfigSaved;

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout(12, 12));

        configLabel = new JLabel("", SwingConstants.LEFT);
        updateConfigLabel();
        add(configLabel, BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton configButton = new JButton("Configurar");
        configButton.addActionListener(e -> openConfigDialog());
        JButton processButton = new JButton("Procesar");
        processButton.addActionListener(e -> onProcess());
        actions.add(configButton);
        actions.add(processButton);
        add(actions, BorderLayout.SOUTH);

        setSize(700, 180);
        setLocationRelativeTo(null);
    }

    private void openConfigDialog() {
        AppConfig updated = ConfigurationDialog.showDialog(this, currentConfig);
        if (updated != null) {
            currentConfig = updated;
            updateConfigLabel();
            onConfigSaved.accept(updated);
        }
    }

    private void onProcess() {
        if (!currentConfig.hasRequiredConfiguration()) {
            openConfigDialog();
            if (!currentConfig.hasRequiredConfiguration()) {
                return;
            }
        }

        List<Path> files;
        try {
            files = ImageScanner.findImages(currentConfig.inputDir(), currentConfig.maxImages());
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(
                    this,
                    "No se pudo leer la carpeta: " + ex.getMessage(),
                    "Error",
                    JOptionPane.ERROR_MESSAGE
            );
            return;
        }

        if (files.isEmpty()) {
            JOptionPane.showMessageDialog(
                    this,
                    "No se encontraron imagenes ni PDFs en la carpeta seleccionada.",
                    "Sin documentos",
                    JOptionPane.INFORMATION_MESSAGE
            );
            return;
        }

        try {
            List<DocumentInputPreparer.PreparedDocument> preparedDocuments = DocumentInputPreparer.prepare(files);
            ProcessingDialog dialog = new ProcessingDialog(this, currentConfig, preparedDocuments);
            dialog.setVisible(true);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(
                    this,
                    "No se pudo preparar los documentos: " + ex.getMessage(),
                    "Error",
                    JOptionPane.ERROR_MESSAGE
            );
        }
    }

    private void updateConfigLabel() {
        String provider = currentConfig.provider();
        String folder = currentConfig.inputDir().toAbsolutePath().toString();
        String output = currentConfig.outputFile().toAbsolutePath().toString();
        configLabel.setText(
                "<html><b>Proveedor:</b> " + provider
                        + " &nbsp;&nbsp; <b>Carpeta:</b> " + folder
                        + "<br/><b>Salida:</b> " + output + "</html>"
        );
    }
}
