package com.poc.ocr.service;

import com.poc.ocr.config.AppConfig;

import java.util.Locale;

public final class OcrServiceFactory {
    private OcrServiceFactory() {
    }

    public static OcrService fromConfig(AppConfig config) {
        String provider = config.provider() == null ? "" : config.provider().toLowerCase(Locale.ROOT);
        return switch (provider) {
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
