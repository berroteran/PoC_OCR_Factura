package com.poc.ocr.service;

public final class PromptFactory {
    private PromptFactory() {
    }

    public static String invoiceExtractionPrompt() {
        return """
                Tu objetivo principal es detectar y extraer datos de facturas de compra/venta de vehiculos (nuevos o usados).
                Analiza esta imagen con OCR y determina si el documento es una factura de compra/venta de vehiculo.
                Responde SOLO con un JSON valido, sin markdown, sin explicacion adicional.

                Reglas:
                - is_invoice=true solo cuando exista evidencia clara de factura.
                - is_chile_invoice=true solo si hay evidencia de que el documento es de Chile (pais, direccion, RUT, CLP, ciudad chilena, etc).
                - is_vehicle_invoice=true solo si hay evidencia de transaccion vehicular (VIN/chasis/motor/placa/modelo/marca/vehiculo/automovil/concesionario).
                - is_useful_invoice=true SOLO si se cumplen las 3 condiciones: is_invoice=true, is_chile_invoice=true, is_vehicle_invoice=true.
                - Si is_useful_invoice=true, es obligatorio priorizar y completar estos campos clave si son visibles:
                  customer_name, purchase_date o invoice_date, vehicle_year, vehicle_model, vehicle_brand/vehicle_make,
                  vehicle_vin (acepta BIN), engine_number o vehicle_engine_number, vehicle_chassis_number, vehicle_plate.
                - Si no es factura de vehiculo, devuelve is_vehicle_invoice=false.
                - Si no es factura, devuelve is_invoice=false y llena solo campos posibles.
                - invoice_probability debe ser probabilidad de factura en [0,1].
                - chile_invoice_probability debe ser probabilidad de factura de Chile en [0,1].
                - vehicle_invoice_probability debe ser probabilidad de factura de vehiculo en [0,1].
                - No inventes datos. Si no se ve un valor, usa null.
                - Montos numericos sin simbolos (ej: 123.45).
                - Usa fecha en formato YYYY-MM-DD cuando sea posible.
                - currency debe ser codigo ISO (USD, EUR, GTQ, etc) si puede inferirse.
                - confidence debe ser un numero entre 0 y 1.
                - vehicle_condition debe ser NEW, USED, SEMI_NEW o null.
                - Si vehicle_condition=NEW y no hay placa visible, vehicle_plate debe ser "SIN_PLACA" o "NUEVO".

                Prompt negativo (estricto):
                - Si el documento trata principalmente de alimentos, supermercado, carniceria, restaurante, farmacia, ferreteria o consumo general
                  (ejemplo: jamon, queso, arroz, pollo, refrescos, abarrotes), NO lo clasifiques como factura de vehiculo.
                - En esos casos usa is_vehicle_invoice=false.
                - Si ademas no cumple criterios de factura formal, usa is_invoice=false.

                JSON requerido:
                {
                  "document_type": "invoice|receipt|other",
                  "is_invoice": true,
                  "invoice_probability": 0.0,
                  "country_code": "CL|OTRO|null",
                  "is_chile_invoice": true,
                  "chile_invoice_probability": 0.0,
                  "merchant_name": "string|null",
                  "customer_name": "string|null",
                  "is_vehicle_invoice": true,
                  "vehicle_invoice_probability": 0.0,
                  "is_useful_invoice": true,
                  "seller_name": "string|null",
                  "merchant_city": "string|null",
                  "confidence": 0.0,
                  "invoice_number": "string|null",
                  "invoice_date": "YYYY-MM-DD|null",
                  "purchase_date": "YYYY-MM-DD|null",
                  "supplier_name": "string|null",
                  "supplier_tax_id": "string|null",
                  "subtotal": 0.0,
                  "tax": 0.0,
                  "total": 0.0,
                  "total_purchase": 0.0,
                  "currency": "string|null",
                  "vehicle_year": "string|null",
                  "commercial_year": "string|null",
                  "vehicle_model": "string|null",
                  "vehicle_brand": "string|null",
                  "vehicle_make": "string|null",
                  "vehicle_type": "string|null",
                  "vehicle_color": "string|null",
                  "vehicle_condition": "NEW|USED|SEMI_NEW|null",
                  "vehicle_condition_detail": "string|null",
                  "vehicle_vin": "string|null",
                  "vehicle_chassis_number": "string|null",
                  "engine_number": "string|null",
                  "vehicle_engine_number": "string|null",
                  "fuel_type": "string|null",
                  "vehicle_plate": "string|null",
                  "vehicle_mileage": "string|null",
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
