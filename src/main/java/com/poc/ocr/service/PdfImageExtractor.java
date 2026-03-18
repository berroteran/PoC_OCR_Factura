package com.poc.ocr.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.text.PDFTextStripper;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class PdfImageExtractor {
    private PdfImageExtractor() {
    }

    public static PdfExtractionResult extractImages(Path pdfPath) throws IOException {
        List<Path> images = new ArrayList<>();
        boolean hasText;
        try (PDDocument document = Loader.loadPDF(pdfPath.toFile())) {
            hasText = hasVisibleText(document);
            int[] counter = new int[]{1};
            for (PDPage page : document.getPages()) {
                PDResources resources = page.getResources();
                if (resources != null) {
                    extractFromResources(resources, images, pdfPath, counter);
                }
            }
        }
        return new PdfExtractionResult(images, hasText);
    }

    private static boolean hasVisibleText(PDDocument document) throws IOException {
        String text = new PDFTextStripper().getText(document);
        return text != null && !text.isBlank();
    }

    private static void extractFromResources(PDResources resources, List<Path> images, Path sourcePdf, int[] counter) throws IOException {
        for (COSName xObjectName : resources.getXObjectNames()) {
            PDXObject xObject = resources.getXObject(xObjectName);
            if (xObject instanceof PDImageXObject imageObject) {
                BufferedImage image = imageObject.getImage();
                if (image != null) {
                    Path tempFile = createTempImagePath(sourcePdf, counter[0]++);
                    ImageIO.write(image, "png", tempFile.toFile());
                    tempFile.toFile().deleteOnExit();
                    images.add(tempFile);
                }
            } else if (xObject instanceof PDFormXObject formXObject) {
                PDResources nestedResources = formXObject.getResources();
                if (nestedResources != null) {
                    extractFromResources(nestedResources, images, sourcePdf, counter);
                }
            }
        }
    }

    private static Path createTempImagePath(Path sourcePdf, int index) throws IOException {
        String base = sourcePdf.getFileName().toString().replaceAll("[^a-zA-Z0-9_-]", "_");
        return java.nio.file.Files.createTempFile("ocrpdf-" + base + "-" + index + "-", ".png");
    }

    public record PdfExtractionResult(List<Path> images, boolean hasText) {
    }
}

