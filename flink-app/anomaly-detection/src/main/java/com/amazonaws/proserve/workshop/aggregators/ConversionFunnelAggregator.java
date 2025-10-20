package com.amazonaws.proserve.workshop.aggregators;

import com.amazonaws.proserve.workshop.process.model.ConversionMetrics;
import com.amazonaws.proserve.workshop.process.model.Event;
import org.apache.flink.streaming.api.functions.windowing.ProcessWindowFunction;
import org.apache.flink.streaming.api.windowing.windows.TimeWindow;
import org.apache.flink.util.Collector;

public class ConversionFunnelAggregator extends ProcessWindowFunction<Event, ConversionMetrics, Long, TimeWindow> {

    @Override
    public void process(Long key, Context context, Iterable<Event> events, Collector<ConversionMetrics> out) {
        long viewCount = 0, addToCartCount = 0, checkoutCount = 0, purchaseCount = 0;
        
        for (Event event : events) {
            String eventType = event.getEventType();
            if ("product_view".equals(eventType)) viewCount++;
            else if ("add_to_cart".equals(eventType)) addToCartCount++;
            else if ("checkout".equals(eventType)) checkoutCount++;
            else if ("purchase".equals(eventType)) purchaseCount++;
        }
        
        double conversionRate = viewCount > 0 ? (double) purchaseCount / viewCount * 100 : 0.0;
        
        ConversionMetrics metrics = ConversionMetrics.builder()
                .windowStart(context.window().getStart())
                .windowEnd(context.window().getEnd())
                .viewCount(viewCount)
                .userId(key)
                .addToCartCount(addToCartCount)
                .checkoutCount(checkoutCount)
                .purchaseCount(purchaseCount)
                .conversionRate(conversionRate)
                .build();
                
        out.collect(metrics);
    }
}