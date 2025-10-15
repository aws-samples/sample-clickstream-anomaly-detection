package com.amazonaws.proserve.workshop.aggregators;

import com.amazonaws.proserve.workshop.process.model.ProductMetrics;
import com.amazonaws.proserve.workshop.process.model.Event;
import org.apache.flink.streaming.api.functions.windowing.ProcessWindowFunction;
import org.apache.flink.streaming.api.windowing.windows.TimeWindow;
import org.apache.flink.util.Collector;
import java.util.HashSet;
import java.util.Set;

public class ProductPerformanceAggregator extends ProcessWindowFunction<Event, ProductMetrics, String, TimeWindow> {

    @Override
    public void process(String productType, Context context, Iterable<Event> events, Collector<ProductMetrics> out) {
        long totalViews = 0;
        Set<Long> uniqueUsers = new HashSet<>();
        long totalDuration = 0;
        long eventCount = 0;
        
        for (Event event : events) {
            totalViews++;
            uniqueUsers.add(event.getUserid());
            eventCount++;
        }
        
        double avgSessionDuration = eventCount > 0 ? (double) totalDuration / eventCount : 0.0;
        
        ProductMetrics metrics = ProductMetrics.builder()
                .windowStart(context.window().getStart())
                .windowEnd(context.window().getEnd())
                .productType(productType)
                .totalViews(totalViews)
                .uniqueUsers(uniqueUsers.size())
                .avgSessionDuration(avgSessionDuration)
                .build();
                
        out.collect(metrics);
    }
}