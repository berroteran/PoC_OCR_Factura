package com.poc.ocr.config;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

public final class LocalConfigStore {
    private static final Path CONFIG_PATH = Paths.get("config.properties");

    private LocalConfigStore() {
    }

    public static Map<String, String> load() {
        Map<String, String> result = new HashMap<>();
        if (!Files.exists(CONFIG_PATH)) {
            return result;
        }

        Properties properties = new Properties();
        try (InputStream in = Files.newInputStream(CONFIG_PATH)) {
            properties.load(in);
            for (String name : properties.stringPropertyNames()) {
                result.put(name, properties.getProperty(name));
            }
        } catch (IOException e) {
            // Si falla la lectura, seguimos con valores vacios.
        }
        return result;
    }

    public static void save(AppConfig config) {
        Properties properties = new Properties();
        properties.setProperty("provider", config.provider());
        properties.setProperty("input", config.inputDir().toString());
        properties.setProperty("output", config.outputFile().toString());
        properties.setProperty("max-images", Integer.toString(config.maxImages()));
        properties.setProperty("gemini_api_key", nullToEmpty(config.geminiApiKey()));
        properties.setProperty("gemini_model", config.geminiModel());
        properties.setProperty("openai_api_key", nullToEmpty(config.openAiApiKey()));
        properties.setProperty("openai_model", config.openAiModel());

        try (OutputStream out = Files.newOutputStream(CONFIG_PATH)) {
            properties.store(out, "Configuracion local OCR Factura");
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo guardar config.properties: " + e.getMessage(), e);
        }
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}

