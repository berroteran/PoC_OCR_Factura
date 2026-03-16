package com.poc.ocr.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.poc.ocr.model.ExtractionPayload;

public final class JsonUtils {
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private JsonUtils() {
    }

    public static ExtractionPayload parseExtractionPayload(String modelText) throws JsonProcessingException {
        String cleaned = stripCodeFence(modelText).trim();
        try {
            return MAPPER.readValue(cleaned, ExtractionPayload.class);
        } catch (JsonProcessingException directEx) {
            String jsonCandidate = extractFirstJsonObject(cleaned);
            if (jsonCandidate == null) {
                throw directEx;
            }
            return MAPPER.readValue(jsonCandidate, ExtractionPayload.class);
        }
    }

    private static String stripCodeFence(String text) {
        String trimmed = text == null ? "" : text.trim();
        if (trimmed.startsWith("```") && trimmed.endsWith("```")) {
            String noStart = trimmed.replaceFirst("^```(?:json)?\\s*", "");
            return noStart.replaceFirst("\\s*```$", "");
        }
        return trimmed;
    }

    private static String extractFirstJsonObject(String text) {
        int start = text.indexOf('{');
        if (start < 0) {
            return null;
        }

        int depth = 0;
        boolean inString = false;
        boolean escaped = false;

        for (int i = start; i < text.length(); i++) {
            char c = text.charAt(i);
            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == '"') {
                    inString = false;
                }
            } else {
                if (c == '"') {
                    inString = true;
                } else if (c == '{') {
                    depth++;
                } else if (c == '}') {
                    depth--;
                    if (depth == 0) {
                        return text.substring(start, i + 1);
                    }
                }
            }
        }
        return null;
    }
}

