package com.poc.ocr.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;

public final class MimeTypeUtils {
    private static final Map<String, String> EXTENSION_TO_MIME = Map.of(
            "png", "image/png",
            "jpg", "image/jpeg",
            "jpeg", "image/jpeg",
            "webp", "image/webp",
            "bmp", "image/bmp",
            "tif", "image/tiff",
            "tiff", "image/tiff"
    );

    private MimeTypeUtils() {
    }

    public static String guessMimeType(Path path) {
        try {
            String probe = Files.probeContentType(path);
            if (probe != null && probe.startsWith("image/")) {
                return probe;
            }
        } catch (IOException ignored) {
            // fallback por extension
        }

        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        int idx = name.lastIndexOf('.');
        if (idx > 0 && idx < name.length() - 1) {
            String ext = name.substring(idx + 1);
            return EXTENSION_TO_MIME.getOrDefault(ext, "image/jpeg");
        }
        return "image/jpeg";
    }
}

