package com.poc.ocr.service;

public final class PromptFactory {
    private PromptFactory() {
    }

    public static String invoiceExtractionPrompt() {
        return """
                Analiza esta imagen con OCR y determina si el documento es una factura.
                Responde SOLO con un JSON valido, sin markdown, sin explicacion adicional.

                Reglas:
                - Si no es factura, devuelve is_invoice=false y llena solo campos posibles.
                - No inventes datos. Si no se ve un valor, usa null.
                - Montos numericos sin simbolos (ej: 123.45).
                - Usa fecha en formato YYYY-MM-DD cuando sea posible.
                - currency debe ser codigo ISO (USD, EUR, GTQ, etc) si puede inferirse.
                - confidence debe ser un numero entre 0 y 1.

                JSON requerido:
                {
                  "is_invoice": true,
                  "document_type": "invoice|receipt|other",
                  "confidence": 0.0,
                  "invoice_number": "string|null",
                  "invoice_date": "YYYY-MM-DD|null",
                  "supplier_name": "string|null",
                  "supplier_tax_id": "string|null",
                  "customer_name": "string|null",
                  "subtotal": 0.0,
                  "tax": 0.0,
                  "total": 0.0,
                  "currency": "string|null",
                  "raw_text": "texto OCR completo o parcial",
                  "line_items": [
                    {
                      "description": "string",
                      "quantity": 1.0,
                      "unit_price": 0.0,
                      "total": 0.0
                    }
                  ],
                  "notes": "observaciones relevantes|null"
                }
                """;
    }
}

