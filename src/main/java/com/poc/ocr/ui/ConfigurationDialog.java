package com.poc.ocr.ui;

import com.poc.ocr.config.AppConfig;
import com.poc.ocr.service.OcrConnectionTester;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingWorker;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.logging.Logger;

public final class ConfigurationDialog extends JDialog {
    private static final Logger LOGGER = Logger.getLogger(ConfigurationDialog.class.getName());
    private static final String DEFAULT_PROVIDER = "gemini";
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
    private final JLabel activeProviderLabel;
    private final JLabel connectionStatusLabel;
    private final JTabbedPane providerTabs;
    private final JTextField geminiApiUrlField;
    private final JComboBox<String> geminiModelCombo;
    private final JPasswordField geminiApiKeyField;
    private final JTextField openAiApiUrlField;
    private final JComboBox<String> openAiModelCombo;
    private final JPasswordField openAiApiKeyField;
    private final JTextField bancoApiUrlField;
    private final JComboBox<String> bancoModelCombo;
    private final JPasswordField bancoApiKeyField;
    private final JButton testConnectionButton;
    private final JButton saveButton;
    private final AppConfig baseConfig;

    private boolean saved;
    private AppConfig result;
    private boolean syncingProviderSelection;
    private SwingWorker<OcrConnectionTester.ConnectionTestResult, Void> connectionTestWorker;

    private ConfigurationDialog(Frame owner, AppConfig baseConfig) {
        super(owner, "Configuracion OCR Factura", true);
        this.baseConfig = baseConfig;

        inputDirField = new JTextField(baseConfig.inputDir().toString(), 34);
        providerCombo = new JComboBox<>(new String[]{"gemini", "openai", "banco"});
        providerCombo.setSelectedItem(normalizedProvider(baseConfig.provider()));
        activeProviderLabel = new JLabel();
        connectionStatusLabel = new JLabel("Sin prueba de conexion.");
        connectionStatusLabel.setForeground(new Color(0x444444));

        geminiApiUrlField = new JTextField(valueOrEmpty(baseConfig.geminiApiUrl()), 30);
        geminiModelCombo = createModelCombo(GEMINI_MODELS, baseConfig.geminiModel());
        geminiApiKeyField = new JPasswordField(valueOrEmpty(baseConfig.geminiApiKey()), 30);

        openAiApiUrlField = new JTextField(valueOrEmpty(baseConfig.openAiApiUrl()), 30);
        openAiModelCombo = createModelCombo(OPENAI_MODELS, baseConfig.openAiModel());
        openAiApiKeyField = new JPasswordField(valueOrEmpty(baseConfig.openAiApiKey()), 30);

        bancoApiUrlField = new JTextField(valueOrEmpty(baseConfig.bancoApiUrl()), 30);
        bancoModelCombo = createModelCombo(BANCO_MODELS, baseConfig.bancoModel());
        bancoApiKeyField = new JPasswordField(valueOrEmpty(baseConfig.bancoApiKey()), 30);

        providerTabs = buildProviderTabs();
        testConnectionButton = new JButton("Probar conexion");
        saveButton = new JButton("Guardar y continuar");

        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout(0, 0));

        JPanel contentPanel = new JPanel(new BorderLayout(10, 10));
        contentPanel.setBorder(new EmptyBorder(12, 12, 12, 12));

        JLabel title = new JLabel("Completa la configuracion para procesar facturas", SwingConstants.LEFT);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 28f / 2f));
        contentPanel.add(title, BorderLayout.NORTH);

        JPanel centerPanel = new JPanel();
        centerPanel.setLayout(new BoxLayout(centerPanel, BoxLayout.Y_AXIS));
        centerPanel.add(buildGeneralPanel());
        centerPanel.add(Box.createVerticalStrut(8));
        centerPanel.add(providerTabs);
        contentPanel.add(centerPanel, BorderLayout.CENTER);
        add(contentPanel, BorderLayout.CENTER);

        JPanel footerPanel = new JPanel(new BorderLayout(8, 8));
        footerPanel.setBorder(new EmptyBorder(0, 12, 12, 12));
        footerPanel.add(connectionStatusLabel, BorderLayout.WEST);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        JButton cancelButton = new JButton("Cancelar");
        cancelButton.addActionListener(e -> dispose());
        testConnectionButton.addActionListener(e -> onTestConnection());
        saveButton.addActionListener(e -> onSave());
        buttonPanel.add(testConnectionButton);
        buttonPanel.add(cancelButton);
        buttonPanel.add(saveButton);
        footerPanel.add(buttonPanel, BorderLayout.EAST);
        add(footerPanel, BorderLayout.SOUTH);

        registerListeners();
        updateProviderFromComboSelection();
        updateActiveProviderIndicator();

        setMinimumSize(new Dimension(930, 660));
        pack();
        setLocationRelativeTo(owner);
    }

    public static AppConfig showDialog(Frame owner, AppConfig initialConfig) {
        ConfigurationDialog dialog = new ConfigurationDialog(owner, initialConfig);
        dialog.setVisible(true);
        return dialog.saved ? dialog.result : null;
    }

    private JPanel buildGeneralPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createTitledBorder("General"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 6, 6, 6);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.WEST;

        gbc.gridx = 0;
        gbc.gridy = 0;
        panel.add(new JLabel("Carpeta de imagenes"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1;
        panel.add(inputDirField, gbc);

        JButton browseButton = new JButton("Buscar...");
        browseButton.addActionListener(e -> onBrowseFolder());
        gbc.gridx = 2;
        gbc.weightx = 0;
        panel.add(browseButton, gbc);

        gbc.gridx = 0;
        gbc.gridy = 1;
        panel.add(new JLabel("Proveedor OCR"), gbc);

        gbc.gridx = 1;
        gbc.gridwidth = 2;
        panel.add(providerCombo, gbc);

        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.gridwidth = 3;
        activeProviderLabel.setBorder(new EmptyBorder(2, 2, 2, 2));
        panel.add(activeProviderLabel, gbc);
        return panel;
    }

    private JTabbedPane buildProviderTabs() {
        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Gemini", buildProviderPanel(geminiApiUrlField, geminiModelCombo, geminiApiKeyField));
        tabs.addTab("OpenAI", buildProviderPanel(openAiApiUrlField, openAiModelCombo, openAiApiKeyField));
        tabs.addTab("Banco", buildProviderPanel(bancoApiUrlField, bancoModelCombo, bancoApiKeyField));
        return tabs;
    }

    private static JPanel buildProviderPanel(
            JTextField apiUrlField,
            JComboBox<String> modelCombo,
            JPasswordField apiKeyField
    ) {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(new EmptyBorder(10, 10, 10, 10));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 6, 6, 6);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.WEST;

        gbc.gridx = 0;
        gbc.gridy = 0;
        panel.add(new JLabel("API URL"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1;
        panel.add(apiUrlField, gbc);

        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.weightx = 0;
        panel.add(new JLabel("Modelo"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1;
        panel.add(modelCombo, gbc);

        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.weightx = 0;
        panel.add(new JLabel("API Key"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1;
        panel.add(apiKeyField, gbc);
        return panel;
    }

    private void registerListeners() {
        providerCombo.addActionListener(e -> updateProviderFromComboSelection());
        providerTabs.addChangeListener(e -> updateProviderFromTabSelection());
        geminiModelCombo.addActionListener(e -> updateActiveProviderIndicator());
        openAiModelCombo.addActionListener(e -> updateActiveProviderIndicator());
        bancoModelCombo.addActionListener(e -> updateActiveProviderIndicator());
    }

    private void updateProviderFromComboSelection() {
        if (syncingProviderSelection) {
            return;
        }
        syncingProviderSelection = true;
        try {
            providerTabs.setSelectedIndex(providerToTabIndex(selectedProvider()));
            updateActiveProviderIndicator();
        } finally {
            syncingProviderSelection = false;
        }
    }

    private void updateProviderFromTabSelection() {
        if (syncingProviderSelection) {
            return;
        }
        syncingProviderSelection = true;
        try {
            String provider = tabIndexToProvider(providerTabs.getSelectedIndex());
            providerCombo.setSelectedItem(provider);
            updateActiveProviderIndicator();
        } finally {
            syncingProviderSelection = false;
        }
    }

    private void updateActiveProviderIndicator() {
        String provider = selectedProvider();
        String model = activeModel(provider);
        String providerText = provider.toUpperCase(Locale.ROOT);
        activeProviderLabel.setText("<html><b>Configuracion activa para procesar:</b> "
                + providerText + " &nbsp;|&nbsp; <b>Modelo:</b> " + model + "</html>");
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

    private void onTestConnection() {
        if (connectionTestWorker != null && !connectionTestWorker.isDone()) {
            return;
        }
        AppConfig candidate = buildCandidateConfig(false);
        if (candidate == null) {
            return;
        }

        String provider = selectedProvider();
        LOGGER.info("Connection test requested from UI | provider=" + provider + " | model=" + activeModel(provider));
        setTestingState(true, "Probando conexion...");

        connectionTestWorker = new SwingWorker<>() {
            @Override
            protected OcrConnectionTester.ConnectionTestResult doInBackground() {
                return OcrConnectionTester.test(candidate);
            }

            @Override
            protected void done() {
                setTestingState(false, null);
                try {
                    OcrConnectionTester.ConnectionTestResult testResult = get();
                    if (testResult.success()) {
                        setConnectionStatus(
                                "Conexion OK: " + testResult.provider().toUpperCase(Locale.ROOT)
                                        + " | HTTP " + testResult.statusCode()
                                        + " | " + testResult.elapsedMs() + " ms",
                                new Color(0x0b5ed7)
                        );
                        JOptionPane.showMessageDialog(
                                ConfigurationDialog.this,
                                "Conexion exitosa para " + testResult.provider().toUpperCase(Locale.ROOT)
                                        + " con modelo " + testResult.model() + ".",
                                "Test de conexion",
                                JOptionPane.INFORMATION_MESSAGE
                        );
                    } else {
                        setConnectionStatus(
                                "Fallo de conexion: " + testResult.message(),
                                new Color(0xc00000)
                        );
                        JOptionPane.showMessageDialog(
                                ConfigurationDialog.this,
                                testResult.message(),
                                "Test de conexion",
                                JOptionPane.WARNING_MESSAGE
                        );
                    }
                } catch (Exception e) {
                    String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                    setConnectionStatus("Error inesperado en test de conexion: " + message, new Color(0xc00000));
                    LOGGER.warning("Connection test UI failure | error=" + message);
                }
            }
        };
        connectionTestWorker.execute();
    }

    private void onSave() {
        AppConfig candidate = buildCandidateConfig(true);
        if (candidate == null) {
            return;
        }
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

    private AppConfig buildCandidateConfig(boolean requireInputDir) {
        String provider = selectedProvider();
        String inputDirValue = inputDirField.getText().trim();
        if (requireInputDir && AppConfig.isBlank(inputDirValue)) {
            JOptionPane.showMessageDialog(
                    this,
                    "Debes seleccionar una carpeta de imagenes.",
                    "Validacion",
                    JOptionPane.WARNING_MESSAGE
            );
            return null;
        }

        Path inputDir;
        try {
            inputDir = AppConfig.isBlank(inputDirValue) ? baseConfig.inputDir() : Paths.get(inputDirValue);
        } catch (InvalidPathException e) {
            JOptionPane.showMessageDialog(
                    this,
                    "La ruta de carpeta no es valida: " + e.getInput(),
                    "Validacion",
                    JOptionPane.WARNING_MESSAGE
            );
            return null;
        }

        String geminiKey = new String(geminiApiKeyField.getPassword()).trim();
        String geminiUrl = geminiApiUrlField.getText().trim();
        String geminiModel = selectedComboValue(geminiModelCombo, "gemini-3.1-flash-lite-preview");
        String openAiKey = new String(openAiApiKeyField.getPassword()).trim();
        String openAiUrl = openAiApiUrlField.getText().trim();
        String openAiModel = selectedComboValue(openAiModelCombo, "gpt-4.1-mini");
        String bancoKey = new String(bancoApiKeyField.getPassword()).trim();
        String bancoUrl = bancoApiUrlField.getText().trim();
        String bancoModel = selectedComboValue(bancoModelCombo, "banco-ocr-v1");

        return baseConfig.withValues(
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
    }

    private void setTestingState(boolean testing, String status) {
        testConnectionButton.setEnabled(!testing);
        saveButton.setEnabled(!testing);
        if (status != null) {
            setConnectionStatus(status, new Color(0x444444));
        }
    }

    private void setConnectionStatus(String text, Color color) {
        connectionStatusLabel.setText(text);
        connectionStatusLabel.setForeground(color);
    }

    private String selectedProvider() {
        Object value = providerCombo.getSelectedItem();
        if (value == null) {
            return DEFAULT_PROVIDER;
        }
        String selected = value.toString().trim().toLowerCase(Locale.ROOT);
        if (!selected.equals("gemini") && !selected.equals("openai") && !selected.equals("banco")) {
            return DEFAULT_PROVIDER;
        }
        return selected;
    }

    private String activeModel(String provider) {
        return switch (provider) {
            case "openai" -> selectedComboValue(openAiModelCombo, "gpt-4.1-mini");
            case "banco" -> selectedComboValue(bancoModelCombo, "banco-ocr-v1");
            default -> selectedComboValue(geminiModelCombo, "gemini-3.1-flash-lite-preview");
        };
    }

    private static int providerToTabIndex(String provider) {
        return switch (provider) {
            case "openai" -> 1;
            case "banco" -> 2;
            default -> 0;
        };
    }

    private static String tabIndexToProvider(int tabIndex) {
        return switch (tabIndex) {
            case 1 -> "openai";
            case 2 -> "banco";
            default -> "gemini";
        };
    }

    private static String normalizedProvider(String provider) {
        if (provider == null || provider.isBlank()) {
            return DEFAULT_PROVIDER;
        }
        String normalized = provider.trim().toLowerCase(Locale.ROOT);
        if (!normalized.equals("gemini") && !normalized.equals("openai") && !normalized.equals("banco")) {
            return DEFAULT_PROVIDER;
        }
        return normalized;
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
