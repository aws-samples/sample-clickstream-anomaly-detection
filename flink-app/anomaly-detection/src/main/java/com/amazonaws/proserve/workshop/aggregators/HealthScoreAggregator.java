package com.amazonaws.proserve.workshop.aggregators;

import com.amazonaws.proserve.workshop.process.model.HealthMetrics;
import com.amazonaws.proserve.workshop.process.model.Event;
import org.apache.flink.streaming.api.functions.windowing.ProcessWindowFunction;
import org.apache.flink.streaming.api.windowing.windows.TimeWindow;
import org.apache.flink.util.Collector;
import java.util.HashMap;
import java.util.Map;

public class HealthScoreAggregator extends ProcessWindowFunction<Event, HealthMetrics, Long, TimeWindow> {

    @Override
    public void process(Long key, Context context, Iterable<Event> events, Collector<HealthMetrics> out) {
        long totalEvents = 0;
        long totalResponseTime = 0;
        Map<String, Long> errorsByType = new HashMap<>();
        
        for (Event event : events) {
            totalEvents++;
            
            // Calculate response time from sequence gaps
            if (event.getPrevglobalseq() != null && event.getGlobalseq() != null) {
                long responseTime = event.getGlobalseq() - event.getPrevglobalseq();
                totalResponseTime += responseTime;
            }
        }
        
        double avgResponseTime = totalEvents > 0 ? (double) totalResponseTime / totalEvents : 0.0;
        double healthScore = Math.max(0, 100 - (avgResponseTime / 10)); // Simple health calculation
        
        HealthMetrics metrics = HealthMetrics.builder()
                .windowStart(context.window().getStart())
                .windowEnd(context.window().getEnd())
                .totalEvents(totalEvents)
                .avgResponseTime(avgResponseTime)
                .healthScore(healthScore)
                .errorsByType(errorsByType)
                .build();
                
        out.collect(metrics);
    }
}