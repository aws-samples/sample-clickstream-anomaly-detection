package com.amazonaws.proserve.workshop.process.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.jackson.Jacksonized;
import java.util.Map;

@Data
@Builder
@Jacksonized
@NoArgsConstructor
@AllArgsConstructor
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