package com.poc.ocr;

import com.poc.ocr.config.AppConfig;
import com.poc.ocr.config.LocalConfigStore;
import com.poc.ocr.ui.ConfigurationDialog;
import com.poc.ocr.ui.MainFrame;

import javax.swing.SwingUtilities;
import java.awt.GraphicsEnvironment;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

public final class Main {
    private Main() {
    }

    public static void main(String[] args) {
        Map<String, String> storedValues = LocalConfigStore.load();
        AppConfig config = AppConfig.fromSources(args, storedValues);

        if (GraphicsEnvironment.isHeadless()) {
            System.err.println("Este entorno no soporta interfaz grafica (Swing).");
            return;
        }

        if (!config.hasRequiredConfiguration()) {
            config = askConfigurationWithUi(config);
            if (config == null) {
                System.out.println("Operacion cancelada por usuario.");
                return;
            }
            LocalConfigStore.save(config);
        }

        AppConfig finalConfig = config;
        SwingUtilities.invokeLater(() -> {
            MainFrame frame = new MainFrame(
                    finalConfig,
                    updated -> {
                        LocalConfigStore.save(updated);
                    }
            );
            frame.setVisible(true);
        });
    }

    private static AppConfig askConfigurationWithUi(AppConfig currentConfig) {
        AtomicReference<AppConfig> resultRef = new AtomicReference<>();
        AppConfig config;
        try {
            SwingUtilities.invokeAndWait(() -> {
                AppConfig configured = ConfigurationDialog.showDialog(null, currentConfig);
                resultRef.set(configured);
            });
        } catch (Exception e) {
            System.err.println("No se pudo abrir la ventana de configuracion: " + e.getMessage());
            return null;
        }
        config = resultRef.get();
        return config;
    }
}
