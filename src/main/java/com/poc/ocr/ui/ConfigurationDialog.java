package com.poc.ocr.ui;

import com.poc.ocr.config.AppConfig;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.nio.file.Path;
import java.nio.file.Paths;

public final class ConfigurationDialog extends JDialog {
    private static final String[] GEMINI_MODELS = {
            "gemini-3.1-flash-lite-preview",
            "gemini-3.1-flash-lite",
            "gemini-2.5-flash",
            "gemini-2.5-pro",
            "gemini-2.0-flash",
            "gemini-2.0-flash-lite"
    };
    private static final String[] OPENAI_MODELS = {
            "gpt-4.1-mini",
            "gpt-4.1",
            "gpt-4.1-nano",
            "gpt-4o-mini",
            "gpt-4o",
            "gpt-5-mini",
            "gpt-5",
            "gpt-5-nano"
    };
    private static final String[] BANCO_MODELS = {
            "banco-ocr-v1",
            "banco-ocr-v2"
    };

    private final JTextField inputDirField;
    private final JComboBox<String> providerCombo;
    private final JTextField geminiApiUrlField;
    private final JComboBox<String> geminiModelCombo;
    private final JPasswordField geminiApiKeyField;
    private final JTextField openAiApiUrlField;
    private final JComboBox<String> openAiModelCombo;
    private final JPasswordField openAiApiKeyField;
    private final JTextField bancoApiUrlField;
    private final JComboBox<String> bancoModelCombo;
    private final JPasswordField bancoApiKeyField;
    private final AppConfig baseConfig;

    private boolean saved;
    private AppConfig result;

    private ConfigurationDialog(Frame owner, AppConfig baseConfig) {
        super(owner, "Configuracion OCR Factura", true);
        this.baseConfig = baseConfig;

        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout(12, 12));

        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 6, 6, 6);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.WEST;

        JLabel title = new JLabel("Completa la configuracion para procesar facturas", SwingConstants.LEFT);
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 3;
        form.add(title, gbc);

        gbc.gridwidth = 1;
        gbc.gridy++;
        gbc.gridx = 0;
        form.add(new JLabel("Carpeta de imagenes"), gbc);

        inputDirField = new JTextField(baseConfig.inputDir().toString(), 30);
        gbc.gridx = 1;
        gbc.weightx = 1;
        form.add(inputDirField, gbc);

        JButton browseButton = new JButton("Buscar...");
        browseButton.addActionListener(e -> onBrowseFolder());
        gbc.gridx = 2;
        gbc.weightx = 0;
        form.add(browseButton, gbc);

        gbc.gridy++;
        gbc.gridx = 0;
        form.add(new JLabel("Proveedor OCR"), gbc);

        providerCombo = new JComboBox<>(new String[]{"gemini", "openai", "banco"});
        providerCombo.setSelectedItem(baseConfig.provider() == null ? "gemini" : baseConfig.provider());
        gbc.gridx = 1;
        gbc.gridwidth = 2;
        form.add(providerCombo, gbc);

        gbc.gridwidth = 1;
        gbc.gridy++;
        gbc.gridx = 0;
        form.add(new JLabel("Gemini API URL"), gbc);

        geminiApiUrlField = new JTextField(valueOrEmpty(baseConfig.geminiApiUrl()), 30);
        gbc.gridx = 1;
        gbc.gridwidth = 2;
        form.add(geminiApiUrlField, gbc);

        gbc.gridwidth = 1;
        gbc.gridy++;
        gbc.gridx = 0;
        form.add(new JLabel("Gemini Modelo"), gbc);

        geminiModelCombo = createModelCombo(GEMINI_MODELS, baseConfig.geminiModel());
        gbc.gridx = 1;
        gbc.gridwidth = 2;
        form.add(geminiModelCombo, gbc);

        gbc.gridwidth = 1;
        gbc.gridy++;
        gbc.gridx = 0;
        form.add(new JLabel("Gemini API Key"), gbc);

        geminiApiKeyField = new JPasswordField(valueOrEmpty(baseConfig.geminiApiKey()), 30);
        gbc.gridx = 1;
        gbc.gridwidth = 2;
        form.add(geminiApiKeyField, gbc);

        gbc.gridwidth = 1;
        gbc.gridy++;
        gbc.gridx = 0;
        form.add(new JLabel("OpenAI API URL"), gbc);

        openAiApiUrlField = new JTextField(valueOrEmpty(baseConfig.openAiApiUrl()), 30);
        gbc.gridx = 1;
        gbc.gridwidth = 2;
        form.add(openAiApiUrlField, gbc);

        gbc.gridwidth = 1;
        gbc.gridy++;
        gbc.gridx = 0;
        form.add(new JLabel("OpenAI Modelo"), gbc);

        openAiModelCombo = createModelCombo(OPENAI_MODELS, baseConfig.openAiModel());
        gbc.gridx = 1;
        gbc.gridwidth = 2;
        form.add(openAiModelCombo, gbc);

        gbc.gridwidth = 1;
        gbc.gridy++;
        gbc.gridx = 0;
        form.add(new JLabel("OpenAI API Key"), gbc);

        openAiApiKeyField = new JPasswordField(valueOrEmpty(baseConfig.openAiApiKey()), 30);
        gbc.gridx = 1;
        gbc.gridwidth = 2;
        form.add(openAiApiKeyField, gbc);

        gbc.gridwidth = 1;
        gbc.gridy++;
        gbc.gridx = 0;
        form.add(new JLabel("Banco API URL"), gbc);

        bancoApiUrlField = new JTextField(valueOrEmpty(baseConfig.bancoApiUrl()), 30);
        gbc.gridx = 1;
        gbc.gridwidth = 2;
        form.add(bancoApiUrlField, gbc);

        gbc.gridwidth = 1;
        gbc.gridy++;
        gbc.gridx = 0;
        form.add(new JLabel("Banco Modelo"), gbc);

        bancoModelCombo = createModelCombo(BANCO_MODELS, baseConfig.bancoModel());
        gbc.gridx = 1;
        gbc.gridwidth = 2;
        form.add(bancoModelCombo, gbc);

        gbc.gridwidth = 1;
        gbc.gridy++;
        gbc.gridx = 0;
        form.add(new JLabel("Banco API Key"), gbc);

        bancoApiKeyField = new JPasswordField(valueOrEmpty(baseConfig.bancoApiKey()), 30);
        gbc.gridx = 1;
        gbc.gridwidth = 2;
        form.add(bancoApiKeyField, gbc);

        add(form, BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton cancelButton = new JButton("Cancelar");
        cancelButton.addActionListener(e -> dispose());
        JButton saveButton = new JButton("Guardar y continuar");
        saveButton.addActionListener(e -> onSave());
        buttons.add(cancelButton);
        buttons.add(saveButton);
        add(buttons, BorderLayout.SOUTH);

        pack();
        setLocationRelativeTo(owner);
    }

    public static AppConfig showDialog(Frame owner, AppConfig initialConfig) {
        ConfigurationDialog dialog = new ConfigurationDialog(owner, initialConfig);
        dialog.setVisible(true);
        return dialog.saved ? dialog.result : null;
    }

    private void onBrowseFolder() {
        JFileChooser chooser = new JFileChooser(inputDirField.getText().trim());
        chooser.setDialogTitle("Selecciona carpeta de imagenes");
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setAcceptAllFileFilterUsed(false);

        int selected = chooser.showOpenDialog(this);
        if (selected == JFileChooser.APPROVE_OPTION && chooser.getSelectedFile() != null) {
            inputDirField.setText(chooser.getSelectedFile().getAbsolutePath());
        }
    }

    private void onSave() {
        String provider = (String) providerCombo.getSelectedItem();
        String inputDirValue = inputDirField.getText().trim();
        String geminiKey = new String(geminiApiKeyField.getPassword()).trim();
        String geminiUrl = geminiApiUrlField.getText().trim();
        String geminiModel = selectedComboValue(geminiModelCombo, "gemini-3.1-flash-lite-preview");
        String openAiKey = new String(openAiApiKeyField.getPassword()).trim();
        String openAiUrl = openAiApiUrlField.getText().trim();
        String openAiModel = selectedComboValue(openAiModelCombo, "gpt-4.1-mini");
        String bancoKey = new String(bancoApiKeyField.getPassword()).trim();
        String bancoUrl = bancoApiUrlField.getText().trim();
        String bancoModel = selectedComboValue(bancoModelCombo, "banco-ocr-v1");

        if (AppConfig.isBlank(inputDirValue)) {
            JOptionPane.showMessageDialog(this, "Debes seleccionar una carpeta de imagenes.", "Validacion", JOptionPane.WARNING_MESSAGE);
            return;
        }

        Path inputDir = Paths.get(inputDirValue);
        AppConfig candidate = baseConfig.withValues(
                provider,
                inputDir,
                geminiKey,
                geminiUrl,
                geminiModel,
                openAiKey,
                openAiUrl,
                openAiModel,
                bancoKey,
                bancoUrl,
                bancoModel
        );
        if (!candidate.hasRequiredConfiguration()) {
            JOptionPane.showMessageDialog(
                    this,
                    String.join("\n", candidate.validationErrors()),
                    "Configuracion incompleta",
                    JOptionPane.WARNING_MESSAGE
            );
            return;
        }

        this.result = candidate;
        this.saved = true;
        dispose();
    }

    private static String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }

    private static JComboBox<String> createModelCombo(String[] values, String selectedValue) {
        JComboBox<String> combo = new JComboBox<>(values);
        combo.setEditable(true);
        if (!AppConfig.isBlank(selectedValue)) {
            combo.setSelectedItem(selectedValue.trim());
        }
        return combo;
    }

    private static String selectedComboValue(JComboBox<String> combo, String fallback) {
        Object value = combo.getSelectedItem();
        if (value == null) {
            return fallback;
        }
        String text = value.toString().trim();
        return text.isEmpty() ? fallback : text;
    }
}
