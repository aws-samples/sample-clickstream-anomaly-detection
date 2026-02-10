package com.amazonaws.proserve.workshop.aggregators;

import com.amazonaws.proserve.workshop.process.model.TrendMetrics;
import com.amazonaws.proserve.workshop.process.model.PerformanceMetrics;
import org.apache.flink.streaming.api.functions.windowing.ProcessAllWindowFunction;
import org.apache.flink.streaming.api.windowing.windows.TimeWindow;
import org.apache.flink.util.Collector;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Aggregator that calculates performance metrics for TrendMetrics output
 * Latency = output timestamp - window end time (processing delay of the window operator)
 */
public class TrendMetricsPerformanceAggregator extends ProcessAllWindowFunction<TrendMetrics, PerformanceMetrics, TimeWindow> {

    @Override
    public void process(Context context, Iterable<TrendMetrics> metrics, Collector<PerformanceMetrics> out) {
        long totalMessages = 0;
        List<Double> latencies = new ArrayList<>();
        
        for (TrendMetrics metric : metrics) {
            totalMessages++;
            
            // Calculate latency: output time - window end time (how long did the window take to process)
            if (metric.getOutputTimestamp() != null) {
                double latencyMs = metric.getOutputTimestamp() - metric.getWindowEnd();
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
        
        PerformanceMetrics perfMetrics = PerformanceMetrics.builder()
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
        
        out.collect(perfMetrics);
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
