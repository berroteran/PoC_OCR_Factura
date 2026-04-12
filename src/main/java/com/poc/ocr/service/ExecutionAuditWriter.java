package com.poc.ocr.service;

import com.poc.ocr.model.DocumentProcessingResult;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.Locale;

public final class ExecutionAuditWriter {
    private static final String HEADER = String.join(",",
            "fecha_hora",
            "tipo_archivo",
            "nombre_archivo",
            "hash_sha256",
            "modelo_reportado",
            "version_modelo_reportada",
            "modelo_solicitado",
            "version_modelo_solicitada",
            "tiempo_ejecucion_ms",
            "tamano_kib",
            "tamano_mib",
            "certeza_factura_pct",
            "certeza_factura_automovil_pct",
            "certeza_chile_pct",
            "calidad_datos_pct"
    );

    private ExecutionAuditWriter() {
    }

    public static void append(
            Path auditFile,
            Path sourcePath,
            Path analyzedPath,
            DocumentProcessingResult result,
            double invoiceCertaintyPct,
            double vehicleInvoiceCertaintyPct,
            double chileCertaintyPct,
            double dataQualityPct
    ) throws IOException {
        Path parent = auditFile.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        OpenOption[] options = new OpenOption[]{
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND
        };

        if (!Files.exists(auditFile)) {
            Files.writeString(auditFile, HEADER + System.lineSeparator(), StandardCharsets.UTF_8, options);
        }

        String row = String.join(",",
                csv(result.processedAt()),
                csv(extension(sourcePath)),
                csv(sourcePath == null ? null : sourcePath.getFileName().toString()),
                csv(sha256Hex(analyzedPath)),
                csv(result.model()),
                csv(result.modelVersion()),
                csv(result.requestedModel()),
                csv(result.requestedModelVersion()),
                csv(result.processingTimeMs() == null ? null : result.processingTimeMs().toString()),
                csv(formatDouble(sizeKiB(result.imageSizeBytes()))),
                csv(formatDouble(sizeMiB(result.imageSizeBytes()))),
                csv(formatDouble(invoiceCertaintyPct)),
                csv(formatDouble(vehicleInvoiceCertaintyPct)),
                csv(formatDouble(chileCertaintyPct)),
                csv(formatDouble(dataQualityPct))
        );
        Files.writeString(auditFile, row + System.lineSeparator(), StandardCharsets.UTF_8, options);
    }

    private static String extension(Path path) {
        if (path == null || path.getFileName() == null) {
            return "";
        }
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        int idx = name.lastIndexOf('.');
        if (idx < 0 || idx == name.length() - 1) {
            return "";
        }
        return name.substring(idx + 1);
    }

    private static String sha256Hex(Path path) {
        if (path == null || !Files.exists(path)) {
            return "";
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = Files.readAllBytes(path);
            byte[] hash = digest.digest(bytes);
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private static Double sizeKiB(Long bytes) {
        if (bytes == null) {
            return null;
        }
        return bytes / 1024d;
    }

    private static Double sizeMiB(Long bytes) {
        if (bytes == null) {
            return null;
        }
        return bytes / (1024d * 1024d);
    }

    private static String formatDouble(Double value) {
        if (value == null) {
            return "";
        }
        return String.format(Locale.US, "%.2f", value);
    }

    private static String csv(String value) {
        String safe = value == null ? "" : value;
        return "\"" + safe.replace("\"", "\"\"") + "\"";
    }
}
