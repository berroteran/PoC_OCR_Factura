package com.poc.ocr.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.poc.ocr.config.AppConfig;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.logging.Logger;

public final class OcrConnectionTester {
    private static final Logger LOGGER = Logger.getLogger(OcrConnectionTester.class.getName());
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();
    private static final String DEFAULT_PROVIDER = "gemini";

    private OcrConnectionTester() {
    }

    public static ConnectionTestResult test(AppConfig config) {
        String provider = normalizedProvider(config.provider());
        try {
            return switch (provider) {
                case "gemini" -> testGemini(config);
                case "openai" -> testOpenAiCompatible(provider, config.openAiApiUrl(), config.openAiModel(), config.openAiApiKey());
                case "banco" -> testOpenAiCompatible(provider, config.bancoApiUrl(), config.bancoModel(), config.bancoApiKey());
                default -> failure(provider, null, null, -1, 0, "Proveedor no soportado: " + provider);
            };
        } catch (Exception e) {
            LOGGER.warning("Connection test error | provider=" + provider + " | error=" + safeMessage(e));
            return failure(provider, null, null, -1, 0, "Error de conexion: " + safeMessage(e));
        }
    }

    private static ConnectionTestResult testGemini(AppConfig config) throws Exception {
        String model = requireValue(config.geminiModel(), "Gemini modelo");
        String apiKey = requireValue(config.geminiApiKey(), "Gemini API key");
        String apiBase = requireValue(config.geminiApiUrl(), "Gemini API URL");

        String endpointWithoutKey = buildGeminiEndpointWithoutKey(apiBase, model);
        String endpointWithKey = appendApiKey(endpointWithoutKey, apiKey);

        ObjectNode payload = MAPPER.createObjectNode();
        ArrayNode contents = payload.putArray("contents");
        ObjectNode content = contents.addObject();
        ArrayNode parts = content.putArray("parts");
        parts.addObject().put("text", "health-check");
        payload.putObject("generationConfig").put("maxOutputTokens", 1);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpointWithKey))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
                .build();

        return executeHttpTest("gemini", model, endpointWithoutKey, request);
    }

    private static ConnectionTestResult testOpenAiCompatible(
            String provider,
            String endpointUrl,
            String model,
            String apiKey
    ) throws Exception {
        String resolvedModel = requireValue(model, provider.toUpperCase(Locale.ROOT) + " modelo");
        String resolvedEndpoint = requireValue(endpointUrl, provider.toUpperCase(Locale.ROOT) + " API URL");
        String resolvedApiKey = requireValue(apiKey, provider.toUpperCase(Locale.ROOT) + " API key");

        ObjectNode payload = MAPPER.createObjectNode();
        payload.put("model", resolvedModel);
        payload.put("max_output_tokens", 1);
        ArrayNode input = payload.putArray("input");
        ObjectNode userMessage = input.addObject();
        userMessage.put("role", "user");
        ArrayNode content = userMessage.putArray("content");
        content.addObject()
                .put("type", "input_text")
                .put("text", "health-check");

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(resolvedEndpoint))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + resolvedApiKey)
                .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
                .build();

        return executeHttpTest(provider, resolvedModel, resolvedEndpoint, request);
    }

    private static ConnectionTestResult executeHttpTest(
            String provider,
            String model,
            String endpoint,
            HttpRequest request
    ) throws Exception {
        String safeEndpoint = sanitizeEndpoint(endpoint);
        long startedNs = System.nanoTime();
        int statusCode = -1;
        LOGGER.info("Connection test start | provider=" + provider
                + " | model=" + model
                + " | endpoint=" + safeEndpoint);
        try {
            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            statusCode = response.statusCode();
            long elapsedMs = elapsedMs(startedNs);
            if (statusCode >= 200 && statusCode < 300) {
                LOGGER.info("Connection test success | provider=" + provider
                        + " | model=" + model
                        + " | endpoint=" + safeEndpoint
                        + " | status=" + statusCode
                        + " | elapsed_ms=" + elapsedMs);
                return new ConnectionTestResult(
                        provider,
                        model,
                        safeEndpoint,
                        true,
                        statusCode,
                        elapsedMs,
                        "Conexion exitosa (HTTP " + statusCode + ")"
                );
            }

            String responseSnippet = truncate(response.body(), 350);
            LOGGER.warning("Connection test failed | provider=" + provider
                    + " | model=" + model
                    + " | endpoint=" + safeEndpoint
                    + " | status=" + statusCode
                    + " | elapsed_ms=" + elapsedMs
                    + " | response=" + responseSnippet);
            return new ConnectionTestResult(
                    provider,
                    model,
                    safeEndpoint,
                    false,
                    statusCode,
                    elapsedMs,
                    "Fallo HTTP " + statusCode + ". Respuesta: " + responseSnippet
            );
        } catch (Exception e) {
            long elapsedMs = elapsedMs(startedNs);
            LOGGER.warning("Connection test exception | provider=" + provider
                    + " | model=" + model
                    + " | endpoint=" + safeEndpoint
                    + " | status=" + statusCode
                    + " | elapsed_ms=" + elapsedMs
                    + " | error=" + safeMessage(e));
            return new ConnectionTestResult(
                    provider,
                    model,
                    safeEndpoint,
                    false,
                    statusCode,
                    elapsedMs,
                    "Error de conexion: " + safeMessage(e)
            );
        }
    }

    private static ConnectionTestResult failure(
            String provider,
            String model,
            String endpoint,
            int statusCode,
            long elapsedMs,
            String message
    ) {
        return new ConnectionTestResult(provider, model, sanitizeEndpoint(endpoint), false, statusCode, elapsedMs, message);
    }

    private static String buildGeminiEndpointWithoutKey(String apiBaseUrl, String model) {
        String trimmed = apiBaseUrl.trim();
        if (trimmed.contains("{model}")) {
            return trimmed.replace("{model}", model);
        }
        if (trimmed.endsWith(":generateContent")) {
            return trimmed;
        }
        if (trimmed.endsWith("/")) {
            return trimmed + model + ":generateContent";
        }
        if (trimmed.endsWith("/models")) {
            return trimmed + "/" + model + ":generateContent";
        }
        return trimmed;
    }

    private static String appendApiKey(String endpointWithoutKey, String apiKey) {
        String separator = endpointWithoutKey.contains("?") ? "&" : "?";
        return endpointWithoutKey + separator + "key=" + URLEncoder.encode(apiKey, StandardCharsets.UTF_8);
    }

    private static String requireValue(String value, String fieldName) {
        if (AppConfig.isBlank(value)) {
            throw new IllegalArgumentException("Falta " + fieldName + " para probar conexion.");
        }
        return value.trim();
    }

    private static String sanitizeEndpoint(String endpoint) {
        if (endpoint == null) {
            return "";
        }
        return endpoint.replaceAll("(?i)([?&](?:key|api[_-]?key|token|access_token)=)[^&]+", "$1***");
    }

    private static String truncate(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return "<sin contenido>";
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength) + "...";
    }

    private static long elapsedMs(long startedNs) {
        return (System.nanoTime() - startedNs) / 1_000_000;
    }

    private static String safeMessage(Throwable throwable) {
        String message = throwable.getMessage();
        if (message == null || message.isBlank()) {
            return throwable.getClass().getSimpleName();
        }
        return maskSecrets(message);
    }

    private static String maskSecrets(String text) {
        return text
                .replaceAll("(?i)([?&](?:key|api[_-]?key|token|access_token)=)[^&\\s]+", "$1***")
                .replaceAll("(?i)(Bearer\\s+)[^\\s]+", "$1***");
    }

    private static String normalizedProvider(String provider) {
        if (provider == null || provider.isBlank()) {
            return DEFAULT_PROVIDER;
        }
        return provider.trim().toLowerCase(Locale.ROOT);
    }

    public record ConnectionTestResult(
            String provider,
            String model,
            String endpoint,
            boolean success,
            int statusCode,
            long elapsedMs,
            String message
    ) {
    }
}
