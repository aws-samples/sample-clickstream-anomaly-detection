package com.amazonaws.proserve.workshop.aggregators;

import com.amazonaws.proserve.workshop.process.model.HealthMetrics;
import com.amazonaws.proserve.workshop.process.model.ClickstreamAnomaly;
import org.apache.flink.streaming.api.functions.windowing.ProcessWindowFunction;
import org.apache.flink.streaming.api.windowing.windows.TimeWindow;
import org.apache.flink.util.Collector;
import java.util.HashMap;
import java.util.Map;

public class HealthScoreAggregator extends ProcessWindowFunction<ClickstreamAnomaly, HealthMetrics, Long, TimeWindow> {

    @Override
    public void process(Long key, Context context, Iterable<ClickstreamAnomaly> events, Collector<HealthMetrics> out) {
        long totalEvents = 0;
        long affectedEvents = 0;
        Map<String, Long> anomaliesByType = new HashMap<>();
        
        for (ClickstreamAnomaly event : events) {
            totalEvents++;
            affectedEvents += event.getAffectedEvents().size();

            // Count anomalies by type
            String anomalyType = event.getAnomalyType();
            anomaliesByType.merge(anomalyType, 1L, Long::sum);
        }
        
        double healthScore = Math.max(0, 100 - ((double) affectedEvents / totalEvents)); // Simple health calculation
        
        HealthMetrics metrics = HealthMetrics.builder()
                .windowStart(context.window().getStart())
                .windowEnd(context.window().getEnd())
                .userId(key)
                .totalEvents(totalEvents)
                .healthScore(healthScore)
                .errorsByType(anomaliesByType)
                .build();
                
        out.collect(metrics);
    }
}