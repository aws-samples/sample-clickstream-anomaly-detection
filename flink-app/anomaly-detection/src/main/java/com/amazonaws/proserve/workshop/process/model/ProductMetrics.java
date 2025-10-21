package com.amazonaws.proserve.workshop.process.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;
import lombok.extern.jackson.Jacksonized;

@Data
@Builder
@Jacksonized
public class ProductMetrics {
    @JsonProperty("window_start")
    private long windowStart;
    @JsonProperty("window_end")
    private long windowEnd;
    @JsonProperty("product_type")
    private String productType;
    @JsonProperty("total_views")
    private long totalViews;
    @JsonProperty("unique_users")
    private long uniqueUsers;
    @JsonProperty("avg_session_duration")
    private double avgSessionDuration;
}