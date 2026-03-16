package com.poc.ocr.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.poc.ocr.model.ExtractionPayload;
import com.poc.ocr.util.JsonUtils;
import com.poc.ocr.util.MimeTypeUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Base64;

public final class OpenAIOcrService implements OcrService {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String RESPONSES_API = "https://api.openai.com/v1/responses";

    private final HttpClient httpClient;
    private final String apiKey;
    private final String model;

    public OpenAIOcrService(String apiKey, String model) {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(20))
                .build();
        this.apiKey = apiKey;
        this.model = model;
    }

    @Override
    public ExtractionPayload analyzeImage(Path imagePath) throws Exception {
        String mimeType = MimeTypeUtils.guessMimeType(imagePath);
        String base64Image = Base64.getEncoder().encodeToString(Files.readAllBytes(imagePath));
        String dataUrl = "data:" + mimeType + ";base64," + base64Image;

        ObjectNode payload = MAPPER.createObjectNode();
        payload.put("model", model);
        payload.put("max_output_tokens", 1800);

        ArrayNode input = payload.putArray("input");
        ObjectNode userMessage = input.addObject();
        userMessage.put("role", "user");
        ArrayNode content = userMessage.putArray("content");
        content.addObject()
                .put("type", "input_text")
                .put("text", PromptFactory.invoiceExtractionPrompt());
        content.addObject()
                .put("type", "input_image")
                .put("image_url", dataUrl);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(RESPONSES_API))
                .timeout(Duration.ofSeconds(120))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 300) {
            throw new IllegalStateException(
                    "OpenAI API devolvio " + response.statusCode() + ": " + truncate(response.body())
            );
        }

        String modelText = extractResponseText(response.body());
        return JsonUtils.parseExtractionPayload(modelText);
    }

    @Override
    public String providerName() {
        return "openai";
    }

    @Override
    public String modelName() {
        return model;
    }

    private static String extractResponseText(String json) throws Exception {
        JsonNode root = MAPPER.readTree(json);

        JsonNode outputText = root.get("output_text");
        if (outputText != null && outputText.isTextual() && !outputText.asText().isBlank()) {
            return outputText.asText();
        }

        JsonNode output = root.path("output");
        if (output.isArray()) {
            StringBuilder sb = new StringBuilder();
            for (JsonNode item : output) {
                JsonNode content = item.path("content");
                if (!content.isArray()) {
                    continue;
                }
                for (JsonNode chunk : content) {
                    String type = chunk.path("type").asText("");
                    if (type.equals("output_text") || type.equals("text")) {
                        String text = chunk.path("text").asText("");
                        if (!text.isBlank()) {
                            sb.append(text);
                        }
                    }
                }
            }
            if (!sb.isEmpty()) {
                return sb.toString();
            }
        }

        throw new IllegalStateException("No se pudo extraer texto util de OpenAI. Respuesta: " + truncate(json));
    }

    private static String truncate(String text) {
        if (text == null) {
            return "";
        }
        int limit = 700;
        return text.length() <= limit ? text : text.substring(0, limit) + "...";
    }
}

