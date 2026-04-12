package com.poc.ocr.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.List;

public record ExtractionPayload(
        @JsonProperty("analysis_model") String analysisModel,
        @JsonProperty("analysis_model_version") String analysisModelVersion,
        @JsonProperty("is_invoice") boolean isInvoice,
        @JsonProperty("invoice_probability") Double invoiceProbability,
        @JsonProperty("country_code") String countryCode,
        @JsonProperty("is_chile_invoice") Boolean isChileInvoice,
        @JsonProperty("chile_invoice_probability") Double chileInvoiceProbability,
        @JsonProperty("is_vehicle_invoice") Boolean isVehicleInvoice,
        @JsonProperty("vehicle_invoice_probability") Double vehicleInvoiceProbability,
        @JsonProperty("document_type") String documentType,
        @JsonProperty("confidence") Double confidence,
        @JsonProperty("invoice_number") String invoiceNumber,
        @JsonProperty("invoice_date") String invoiceDate,
        @JsonProperty("purchase_date") String purchaseDate,
        @JsonProperty("merchant_name") String merchantName,
        @JsonProperty("supplier_name") String supplierName,
        @JsonProperty("merchant_city") String merchantCity,
        @JsonProperty("seller_name") String sellerName,
        @JsonProperty("supplier_tax_id") String supplierTaxId,
        @JsonProperty("customer_name") String customerName,
        @JsonProperty("subtotal") BigDecimal subtotal,
        @JsonProperty("tax") BigDecimal tax,
        @JsonProperty("total") BigDecimal total,
        @JsonProperty("total_purchase") BigDecimal totalPurchase,
        @JsonProperty("currency") String currency,
        @JsonProperty("vehicle_condition")
        @JsonAlias({"condition"}) String vehicleCondition,
        @JsonProperty("vehicle_brand") String vehicleBrand,
        @JsonProperty("vehicle_type") String vehicleType,
        @JsonProperty("vehicle_color") String vehicleColor,
        @JsonProperty("commercial_year") String commercialYear,
        @JsonProperty("fuel_type") String fuelType,
        @JsonProperty("engine_number") String engineNumber,
        @JsonProperty("vehicle_condition_detail") String vehicleConditionDetail,
        @JsonProperty("vehicle_make") String vehicleMake,
        @JsonProperty("vehicle_model") String vehicleModel,
        @JsonProperty("vehicle_year") String vehicleYear,
        @JsonProperty("vehicle_vin")
        @JsonAlias({"vin", "bin", "vehicle_bin"}) String vehicleVin,
        @JsonProperty("vehicle_plate") String vehiclePlate,
        @JsonProperty("vehicle_engine_number") String vehicleEngineNumber,
        @JsonProperty("vehicle_chassis_number") String vehicleChassisNumber,
        @JsonProperty("vehicle_mileage") String vehicleMileage,
        @JsonProperty("raw_text") String rawText,
        @JsonProperty("line_items") List<LineItem> lineItems,
        @JsonProperty("notes") String notes
) {
}
