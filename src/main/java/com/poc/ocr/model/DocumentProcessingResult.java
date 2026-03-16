package com.poc.ocr.model;

import java.nio.file.Path;
import java.time.OffsetDateTime;

public record DocumentProcessingResult(
        String fileName,
        String filePath,
        String provider,
        String model,
        String processedAt,
        boolean success,
        String error,
        ExtractionPayload extraction
) {
    public static DocumentProcessingResult success(
            Path path,
            String provider,
            String model,
            OffsetDateTime processedAt,
            ExtractionPayload extraction
    ) {
        return new DocumentProcessingResult(
                path.getFileName().toString(),
                path.toAbsolutePath().toString(),
                provider,
                model,
                processedAt.toString(),
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
            String error
    ) {
        return new DocumentProcessingResult(
                path.getFileName().toString(),
                path.toAbsolutePath().toString(),
                provider,
                model,
                processedAt.toString(),
                false,
                error,
                null
        );
    }
}

