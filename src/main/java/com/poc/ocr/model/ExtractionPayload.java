package com.poc.ocr.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.List;

public record ExtractionPayload(
        @JsonProperty("is_invoice") boolean isInvoice,
        @JsonProperty("document_type") String documentType,
        @JsonProperty("confidence") Double confidence,
        @JsonProperty("invoice_number") String invoiceNumber,
        @JsonProperty("invoice_date") String invoiceDate,
        @JsonProperty("supplier_name") String supplierName,
        @JsonProperty("supplier_tax_id") String supplierTaxId,
        @JsonProperty("customer_name") String customerName,
        @JsonProperty("subtotal") BigDecimal subtotal,
        @JsonProperty("tax") BigDecimal tax,
        @JsonProperty("total") BigDecimal total,
        @JsonProperty("currency") String currency,
        @JsonProperty("raw_text") String rawText,
        @JsonProperty("line_items") List<LineItem> lineItems,
        @JsonProperty("notes") String notes
) {
}

