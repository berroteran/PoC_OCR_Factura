package com.poc.ocr.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

public final class ImageScanner {
    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of(
            "png", "jpg", "jpeg", "webp", "bmp", "tif", "tiff", "pdf"
    );

    private ImageScanner() {
    }

    public static List<Path> findImages(Path inputDir, int maxImages) throws IOException {
        if (!Files.exists(inputDir) || !Files.isDirectory(inputDir)) {
            throw new IllegalArgumentException("La carpeta no existe o no es valida: " + inputDir.toAbsolutePath());
        }

        try (Stream<Path> stream = Files.walk(inputDir)) {
            Stream<Path> filtered = stream
                    .filter(Files::isRegularFile)
                    .filter(ImageScanner::isSupportedImage)
                    .sorted(Comparator.naturalOrder());

            if (maxImages > 0) {
                filtered = filtered.limit(maxImages);
            }
            return filtered.toList();
        }
    }

    private static boolean isSupportedImage(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        int idx = name.lastIndexOf('.');
        if (idx < 0 || idx == name.length() - 1) {
            return false;
        }
        String ext = name.substring(idx + 1);
        return SUPPORTED_EXTENSIONS.contains(ext);
    }
}
