package com.poc.ocr.service;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class DocumentInputPreparer {
    private DocumentInputPreparer() {
    }

    public static List<PreparedDocument> prepare(List<Path> files) throws IOException {
        List<PreparedDocument> prepared = new ArrayList<>();

        for (Path file : files) {
            String extension = extension(file);
            if ("pdf".equals(extension)) {
                PdfImageExtractor.PdfExtractionResult result = PdfImageExtractor.extractImages(file);
                if (result.images().isEmpty()) {
                    String reason = result.hasText()
                            ? "PDF de texto sin imagenes detectadas; sospechoso de posible documento falso."
                            : "PDF sin imagenes extraibles; no se pudo validar como factura escaneada.";
                    prepared.add(new PreparedDocument(
                            file,
                            null,
                            file.getFileName().toString(),
                            true,
                            reason
                    ));
                } else {
                    for (int i = 0; i < result.images().size(); i++) {
                        Path image = result.images().get(i);
                        String displayName = file.getFileName() + " [imagen " + (i + 1) + "/" + result.images().size() + "]";
                        prepared.add(new PreparedDocument(
                                file,
                                image,
                                displayName,
                                false,
                                null
                        ));
                    }
                }
            } else {
                prepared.add(new PreparedDocument(
                        file,
                        file,
                        file.getFileName().toString(),
                        false,
                        null
                ));
            }
        }
        return prepared;
    }

    private static String extension(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        int idx = name.lastIndexOf('.');
        if (idx < 0 || idx == name.length() - 1) {
            return "";
        }
        return name.substring(idx + 1);
    }

    public record PreparedDocument(
            Path sourcePath,
            Path imagePath,
            String displayName,
            boolean suspicious,
            String suspicionReason
    ) {
    }
}

