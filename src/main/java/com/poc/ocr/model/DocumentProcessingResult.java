package com.poc.ocr.model;

import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record DocumentProcessingResult(
        String fileName,
        String filePath,
        String provider,
        String model,
        String modelVersion,
        String processedAt,
        Long imageSizeBytes,
        Integer imageWidth,
        Integer imageHeight,
        Long processingTimeMs,
        boolean success,
        String error,
        ExtractionPayload extraction
) {
    public static DocumentProcessingResult success(
            Path path,
            String provider,
            String model,
            OffsetDateTime processedAt,
            Long imageSizeBytes,
            Integer imageWidth,
            Integer imageHeight,
            long processingTimeMs,
            ExtractionPayload extraction
    ) {
        return new DocumentProcessingResult(
                path.getFileName().toString(),
                path.toAbsolutePath().toString(),
                provider,
                model,
                resolveModelVersion(model),
                processedAt.toString(),
                imageSizeBytes,
                imageWidth,
                imageHeight,
                processingTimeMs,
                true,
                null,
                extraction
        );
    }

    public static DocumentProcessingResult failure(
            Path path,
            String provider,
            String model,
            OffsetDateTime processedAt,
            Long imageSizeBytes,
            Integer imageWidth,
            Integer imageHeight,
            long processingTimeMs,
            String error
    ) {
        return new DocumentProcessingResult(
                path.getFileName().toString(),
                path.toAbsolutePath().toString(),
                provider,
                model,
                resolveModelVersion(model),
                processedAt.toString(),
                imageSizeBytes,
                imageWidth,
                imageHeight,
                processingTimeMs,
                false,
                error,
                null
        );
    }

    private static final Pattern VERSION_WITH_V = Pattern.compile("(?i)\\bv(\\d+(?:\\.\\d+)*)\\b");
    private static final Pattern VERSION_DECIMAL = Pattern.compile("(\\d+(?:\\.\\d+)*)");

    private static String resolveModelVersion(String model) {
        if (model == null || model.isBlank()) {
            return "unknown";
        }
        Matcher matcherWithV = VERSION_WITH_V.matcher(model);
        if (matcherWithV.find()) {
            return "v" + matcherWithV.group(1);
        }
        Matcher matcherDecimal = VERSION_DECIMAL.matcher(model);
        if (matcherDecimal.find()) {
            return matcherDecimal.group(1);
        }
        return "unknown";
    }
}
