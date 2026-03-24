package com.poc.ocr.config;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public record AppConfig(
        String provider,
        Path inputDir,
        Path outputFile,
        String geminiApiKey,
        String geminiApiUrl,
        String geminiModel,
        String openAiApiKey,
        String openAiApiUrl,
        String openAiModel,
        String bancoApiKey,
        String bancoApiUrl,
        String bancoModel,
        int maxImages
) {
    private static final String DEFAULT_GEMINI_API_URL = "https://generativelanguage.googleapis.com/v1beta/models/";
    private static final String DEFAULT_OPENAI_API_URL = "https://api.openai.com/v1/responses";

    public static AppConfig fromEnvironment(String[] args) {
        return fromSources(args, Map.of());
    }

    public static AppConfig fromSources(String[] args, Map<String, String> storedValues) {
        Map<String, String> cli = parseArgs(args);

        String provider = getValue(cli, storedValues, "provider", "OCR_PROVIDER", "gemini").toLowerCase(Locale.ROOT);
        Path inputDir = Paths.get(getValue(cli, storedValues, "input", "INPUT_DIR", "input"));
        Path outputFile = Paths.get(getValue(cli, storedValues, "output", "OUTPUT_FILE", "output/results.json"));
        int maxImages = parseIntOrDefault(getValue(cli, storedValues, "max-images", "MAX_IMAGES", "0"), 0);

        String geminiApiKey = envOrStored("GEMINI_API_KEY", storedValues, "gemini_api_key");
        String geminiApiUrl = envOrStoredDefault("GEMINI_API_URL", storedValues, "gemini_api_url", DEFAULT_GEMINI_API_URL);
        String geminiModel = envOrStoredDefault("GEMINI_MODEL", storedValues, "gemini_model", "gemini-2.0-flash");

        String openAiApiKey = envOrStored("OPENAI_API_KEY", storedValues, "openai_api_key");
        String openAiApiUrl = envOrStoredDefault("OPENAI_API_URL", storedValues, "openai_api_url", DEFAULT_OPENAI_API_URL);
        String openAiModel = envOrStoredDefault("OPENAI_MODEL", storedValues, "openai_model", "gpt-4.1-mini");

        String bancoApiKey = envOrStored("BANCO_API_KEY", storedValues, "banco_api_key");
        String bancoApiUrl = envOrStoredDefault("BANCO_API_URL", storedValues, "banco_api_url", "");
        String bancoModel = envOrStoredDefault("BANCO_MODEL", storedValues, "banco_model", "banco-ocr-v1");

        return new AppConfig(
                provider,
                inputDir,
                outputFile,
                geminiApiKey,
                geminiApiUrl,
                geminiModel,
                openAiApiKey,
                openAiApiUrl,
                openAiModel,
                bancoApiKey,
                bancoApiUrl,
                bancoModel,
                maxImages
        );
    }

    public List<String> validationErrors() {
        List<String> errors = new ArrayList<>();

        if (!provider.equals("gemini") && !provider.equals("openai") && !provider.equals("banco")) {
            errors.add("El proveedor debe ser 'gemini', 'openai' o 'banco'.");
        }
        if (maxImages < 0) {
            errors.add("MAX_IMAGES no puede ser negativo.");
        }
        if (isBlank(inputDir.toString())) {
            errors.add("La carpeta de entrada es obligatoria.");
        } else if (!Files.exists(inputDir) || !Files.isDirectory(inputDir)) {
            errors.add("La carpeta de entrada no existe o no es valida: " + inputDir.toAbsolutePath());
        }

        if (provider.equals("gemini")) {
            if (isBlank(geminiApiKey)) {
                errors.add("Falta la API key de Gemini.");
            }
            if (isBlank(geminiApiUrl)) {
                errors.add("Falta el API URL de Gemini.");
            }
        }
        if (provider.equals("openai")) {
            if (isBlank(openAiApiKey)) {
                errors.add("Falta la API key de OpenAI.");
            }
            if (isBlank(openAiApiUrl)) {
                errors.add("Falta el API URL de OpenAI.");
            }
        }
        if (provider.equals("banco")) {
            if (isBlank(bancoApiKey)) {
                errors.add("Falta la API key de Banco.");
            }
            if (isBlank(bancoApiUrl)) {
                errors.add("Falta el API URL de Banco.");
            }
        }

        return errors;
    }

    public boolean hasRequiredConfiguration() {
        return validationErrors().isEmpty();
    }

    public AppConfig withValues(
            String provider,
            Path inputDir,
            String geminiApiKey,
            String geminiApiUrl,
            String openAiApiKey,
            String openAiApiUrl,
            String bancoApiKey,
            String bancoApiUrl
    ) {
        return new AppConfig(
                provider,
                inputDir,
                outputFile,
                geminiApiKey,
                geminiApiUrl,
                geminiModel,
                openAiApiKey,
                openAiApiUrl,
                openAiModel,
                bancoApiKey,
                bancoApiUrl,
                bancoModel,
                maxImages
        );
    }

    private static Map<String, String> parseArgs(String[] args) {
        Map<String, String> out = new HashMap<>();
        for (String arg : args) {
            if (!arg.startsWith("--")) {
                continue;
            }
            int idx = arg.indexOf('=');
            if (idx < 0) {
                continue;
            }
            String key = arg.substring(2, idx).trim();
            String value = arg.substring(idx + 1).trim();
            out.put(key, value);
        }
        return out;
    }

    private static String getValue(
            Map<String, String> cli,
            Map<String, String> stored,
            String cliKey,
            String envKey,
            String defaultValue
    ) {
        String cliValue = cli.get(cliKey);
        if (!isBlank(cliValue)) {
            return cliValue;
        }
        String envValue = env(envKey);
        if (!isBlank(envValue)) {
            return envValue;
        }
        String storedValue = stored.get(cliKey);
        if (!isBlank(storedValue)) {
            return storedValue;
        }
        return defaultValue;
    }

    private static String env(String key) {
        return System.getenv(key);
    }

    private static String envOrStored(String envKey, Map<String, String> stored, String storedKey) {
        String value = env(envKey);
        if (!isBlank(value)) {
            return value;
        }
        return stored.get(storedKey);
    }

    private static String envOrStoredDefault(String envKey, Map<String, String> stored, String storedKey, String defaultValue) {
        String value = envOrStored(envKey, stored, storedKey);
        if (isBlank(value)) {
            return defaultValue;
        }
        return value;
    }

    public static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static int parseIntOrDefault(String value, int defaultValue) {
        if (isBlank(value)) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}

