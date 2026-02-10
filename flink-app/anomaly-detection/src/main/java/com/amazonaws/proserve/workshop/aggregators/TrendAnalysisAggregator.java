package com.amazonaws.proserve.workshop.aggregators;

import com.amazonaws.proserve.workshop.process.model.Event;
import com.amazonaws.proserve.workshop.process.model.TrendMetrics;
import org.apache.flink.streaming.api.functions.windowing.ProcessWindowFunction;
import org.apache.flink.streaming.api.windowing.windows.TimeWindow;
import org.apache.flink.util.Collector;
import java.util.HashMap;
import java.util.Map;

/**
 * Sliding window aggregator for trend analysis
 * Provides overlapping windows to detect trends over time
 */
public class TrendAnalysisAggregator extends ProcessWindowFunction<Event, TrendMetrics, String, TimeWindow> {

    @Override
    public void process(String productType, Context context, Iterable<Event> events, Collector<TrendMetrics> out) {
        long eventCount = 0;
        Map<String, Long> eventTypeBreakdown = new HashMap<>();
        
        for (Event event : events) {
            eventCount++;
            
            String eventType = event.getEventType();
            if (eventType != null) {
                eventTypeBreakdown.merge(eventType, 1L, Long::sum);
            }
        }
        
        // Calculate events per second for this window
        long windowDurationSeconds = (context.window().getEnd() - context.window().getStart()) / 1000;
        double eventsPerSecond = windowDurationSeconds > 0 ? (double) eventCount / windowDurationSeconds : 0;
        
        TrendMetrics metrics = TrendMetrics.builder()
                .windowStart(context.window().getStart())
                .windowEnd(context.window().getEnd())
                .productType(productType)
                .eventCount(eventCount)
                .eventsPerSecond(eventsPerSecond)
                .eventTypeBreakdown(eventTypeBreakdown)
                .outputTimestamp(System.currentTimeMillis())
                .build();
                
        out.collect(metrics);
    }
}
