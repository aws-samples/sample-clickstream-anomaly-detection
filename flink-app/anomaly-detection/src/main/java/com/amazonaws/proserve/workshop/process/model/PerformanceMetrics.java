package com.amazonaws.proserve.workshop.process.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PerformanceMetrics {
    private long windowStart;
    private long windowEnd;
    private long totalMessages;
    private double messagesPerSecond;
    private double avgLatencyMs;
    private double minLatencyMs;
    private double maxLatencyMs;
    private double p95LatencyMs;
    private double p99LatencyMs;
}
