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
        String geminiModel,
        String openAiApiKey,
        String openAiModel,
        int maxImages
) {
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
        String geminiModel = envOrStoredDefault("GEMINI_MODEL", storedValues, "gemini_model", "gemini-2.0-flash");

        String openAiApiKey = envOrStored("OPENAI_API_KEY", storedValues, "openai_api_key");
        String openAiModel = envOrStoredDefault("OPENAI_MODEL", storedValues, "openai_model", "gpt-4.1-mini");

        return new AppConfig(
                provider,
                inputDir,
                outputFile,
                geminiApiKey,
                geminiModel,
                openAiApiKey,
                openAiModel,
                maxImages
        );
    }

    public List<String> validationErrors() {
        List<String> errors = new ArrayList<>();

        if (!provider.equals("gemini") && !provider.equals("openai")) {
            errors.add("El proveedor debe ser 'gemini' u 'openai'.");
        }
        if (maxImages < 0) {
            errors.add("MAX_IMAGES no puede ser negativo.");
        }
        if (isBlank(inputDir.toString())) {
            errors.add("La carpeta de entrada es obligatoria.");
        } else if (!Files.exists(inputDir) || !Files.isDirectory(inputDir)) {
            errors.add("La carpeta de entrada no existe o no es valida: " + inputDir.toAbsolutePath());
        }
        if (provider.equals("gemini") && isBlank(geminiApiKey)) {
            errors.add("Falta la API key de Gemini.");
        }
        if (provider.equals("openai") && isBlank(openAiApiKey)) {
            errors.add("Falta la API key de OpenAI.");
        }

        return errors;
    }

    public boolean hasRequiredConfiguration() {
        return validationErrors().isEmpty();
    }

    public void validateOrThrow() {
        List<String> errors = validationErrors();
        if (!errors.isEmpty()) {
            throw new IllegalArgumentException(String.join(" | ", errors));
        }
    }

    public AppConfig withValues(
            String provider,
            Path inputDir,
            String geminiApiKey,
            String openAiApiKey
    ) {
        return new AppConfig(
                provider,
                inputDir,
                outputFile,
                geminiApiKey,
                geminiModel,
                openAiApiKey,
                openAiModel,
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
