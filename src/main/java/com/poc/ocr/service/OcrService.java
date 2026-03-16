package com.poc.ocr.service;

import com.poc.ocr.model.ExtractionPayload;

import java.nio.file.Path;

public interface OcrService {
    ExtractionPayload analyzeImage(Path imagePath) throws Exception;

    String providerName();

    String modelName();
}

