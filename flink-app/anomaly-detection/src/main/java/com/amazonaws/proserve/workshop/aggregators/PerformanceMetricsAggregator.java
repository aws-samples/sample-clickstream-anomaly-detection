package com.amazonaws.proserve.workshop.aggregators;

import com.amazonaws.proserve.workshop.process.model.Event;
import com.amazonaws.proserve.workshop.process.model.PerformanceMetrics;
import org.apache.flink.streaming.api.functions.windowing.ProcessAllWindowFunction;
import org.apache.flink.streaming.api.windowing.windows.TimeWindow;
import org.apache.flink.util.Collector;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Aggregator that calculates throughput and latency metrics
 * Latency = ingestion timestamp - event timestamp (time from event creation to Flink arrival)
 */
public class PerformanceMetricsAggregator extends ProcessAllWindowFunction<Event, PerformanceMetrics, TimeWindow> {

    @Override
    public void process(Context context, Iterable<Event> events, Collector<PerformanceMetrics> out) {
        long totalMessages = 0;
        List<Double> latencies = new ArrayList<>();
        
        for (Event event : events) {
            totalMessages++;
            
            // Calculate latency: ingestion time - event time
            if (event.getIngestionTimestamp() != null && event.getEventtimestamp() != null) {
                double latencyMs = event.getIngestionTimestamp() - event.getEventtimestamp();
                latencies.add(latencyMs);
            }
        }
        
        if (totalMessages == 0 || latencies.isEmpty()) {
            return;
        }
        
        // Sort latencies for percentile calculations
        Collections.sort(latencies);
        
        // Calculate statistics
        double avgLatency = latencies.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double minLatency = latencies.get(0);
        double maxLatency = latencies.get(latencies.size() - 1);
        double p95Latency = getPercentile(latencies, 0.95);
        double p99Latency = getPercentile(latencies, 0.99);
        
        // Calculate throughput
        long windowDurationSeconds = (context.window().getEnd() - context.window().getStart()) / 1000;
        double messagesPerSecond = windowDurationSeconds > 0 ? (double) totalMessages / windowDurationSeconds : 0;
        
        PerformanceMetrics metrics = PerformanceMetrics.builder()
                .windowStart(context.window().getStart())
                .windowEnd(context.window().getEnd())
                .totalMessages(totalMessages)
                .messagesPerSecond(messagesPerSecond)
                .avgLatencyMs(avgLatency)
                .minLatencyMs(minLatency)
                .maxLatencyMs(maxLatency)
                .p95LatencyMs(p95Latency)
                .p99LatencyMs(p99Latency)
                .build();
        
        out.collect(metrics);
    }
    
    private double getPercentile(List<Double> sortedValues, double percentile) {
        if (sortedValues.isEmpty()) {
            return 0.0;
        }
        int index = (int) Math.ceil(percentile * sortedValues.size()) - 1;
        index = Math.max(0, Math.min(index, sortedValues.size() - 1));
        return sortedValues.get(index);
    }
}
