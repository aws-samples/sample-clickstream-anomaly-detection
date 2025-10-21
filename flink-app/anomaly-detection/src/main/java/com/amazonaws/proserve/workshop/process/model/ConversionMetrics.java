package com.amazonaws.proserve.workshop.process.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;
import lombok.extern.jackson.Jacksonized;

@Data
@Builder
@Jacksonized
public class ConversionMetrics {
    @JsonProperty("user_id")
    private long userId;
    @JsonProperty("window_start")
    private long windowStart;
    @JsonProperty("window_end")
    private long windowEnd;
    @JsonProperty("view_count")
    private long viewCount;
    @JsonProperty("add_to_cart_count")
    private long addToCartCount;
    @JsonProperty("checkout_count")
    private long checkoutCount;
    @JsonProperty("purchase_count")
    private long purchaseCount;
    @JsonProperty("conversion_rate")
    private double conversionRate;
}