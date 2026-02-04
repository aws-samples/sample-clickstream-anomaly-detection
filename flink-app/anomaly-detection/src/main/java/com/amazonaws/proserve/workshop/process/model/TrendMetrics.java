package com.amazonaws.proserve.workshop.process.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrendMetrics {
    private long windowStart;
    private long windowEnd;
    private String productType;
    private long eventCount;
    private double eventsPerSecond;
    private Map<String, Long> eventTypeBreakdown;
}
