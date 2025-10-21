package com.amazonaws.proserve.workshop.process.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;
import lombok.extern.jackson.Jacksonized;
import java.util.Map;

@Data
@Builder
@Jacksonized
public class HealthMetrics {
    @JsonProperty("user_id")
    private long userId;
    @JsonProperty("window_start")
    private long windowStart;
    @JsonProperty("window_end")
    private long windowEnd;
    @JsonProperty("health_score")
    private double healthScore;
    @JsonProperty("total_events")
    private long totalEvents;
    @JsonProperty("errors_by_type")
    private Map<String, Long> errorsByType;
}