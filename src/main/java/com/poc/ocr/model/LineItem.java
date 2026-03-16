package com.poc.ocr.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

public record LineItem(
        @JsonProperty("description") String description,
        @JsonProperty("quantity") BigDecimal quantity,
        @JsonProperty("unit_price") BigDecimal unitPrice,
        @JsonProperty("total") BigDecimal total
) {
}

