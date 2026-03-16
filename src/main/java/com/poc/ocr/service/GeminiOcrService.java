package com.poc.ocr.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.poc.ocr.model.ExtractionPayload;
import com.poc.ocr.util.JsonUtils;
import com.poc.ocr.util.MimeTypeUtils;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Base64;

public final class GeminiOcrService implements OcrService {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String API_BASE = "https://generativelanguage.googleapis.com/v1beta/models/";

    private final HttpClient httpClient;
    private final String apiKey;
    private final String model;

    public GeminiOcrService(String apiKey, String model) {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(20))
                .build();
        this.apiKey = apiKey;
        this.model = model;
    }

    @Override
    public ExtractionPayload analyzeImage(Path imagePath) throws Exception {
        String base64Image = Base64.getEncoder().encodeToString(Files.readAllBytes(imagePath));
        String mimeType = MimeTypeUtils.guessMimeType(imagePath);

        ObjectNode payload = MAPPER.createObjectNode();
        ArrayNode contents = payload.putArray("contents");
        ObjectNode content = contents.addObject();
        ArrayNode parts = content.putArray("parts");
        parts.addObject().put("text", PromptFactory.invoiceExtractionPrompt());
        ObjectNode inlineData = parts.addObject().putObject("inline_data");
        inlineData.put("mime_type", mimeType);
        inlineData.put("data", base64Image);

        ObjectNode generationConfig = payload.putObject("generationConfig");
        generationConfig.put("responseMimeType", "application/json");

        String endpoint = API_BASE + model + ":generateContent?key=" + URLEncoder.encode(apiKey, StandardCharsets.UTF_8);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .timeout(Duration.ofSeconds(120))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 300) {
            throw new IllegalStateException(
                    "Gemini API devolvio " + response.statusCode() + ": " + truncate(response.body())
            );
        }

        String modelText = extractResponseText(response.body());
        return JsonUtils.parseExtractionPayload(modelText);
    }

    @Override
    public String providerName() {
        return "gemini";
    }

    @Override
    public String modelName() {
        return model;
    }

    private static String extractResponseText(String json) throws Exception {
        JsonNode root = MAPPER.readTree(json);
        JsonNode candidates = root.path("candidates");
        if (!candidates.isArray() || candidates.isEmpty()) {
            throw new IllegalStateException("Gemini no retorno candidates. Respuesta: " + truncate(json));
        }

        JsonNode parts = candidates.get(0).path("content").path("parts");
        if (!parts.isArray() || parts.isEmpty()) {
            throw new IllegalStateException("Gemini no retorno parts de contenido. Respuesta: " + truncate(json));
        }

        StringBuilder sb = new StringBuilder();
        for (JsonNode part : parts) {
            JsonNode textNode = part.get("text");
            if (textNode != null && textNode.isTextual()) {
                sb.append(textNode.asText());
            }
        }

        if (sb.isEmpty()) {
            throw new IllegalStateException("Gemini no retorno texto util. Respuesta: " + truncate(json));
        }
        return sb.toString();
    }

    private static String truncate(String text) {
        if (text == null) {
            return "";
        }
        int limit = 700;
        return text.length() <= limit ? text : text.substring(0, limit) + "...";
    }
}

