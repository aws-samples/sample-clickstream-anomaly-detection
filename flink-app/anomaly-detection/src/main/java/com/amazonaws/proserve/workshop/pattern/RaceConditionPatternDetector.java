package com.amazonaws.proserve.workshop.pattern;

import com.amazonaws.proserve.workshop.process.model.Event;
import com.amazonaws.proserve.workshop.process.model.ClickstreamAnomaly;
import lombok.extern.slf4j.Slf4j;
import org.apache.flink.cep.nfa.aftermatch.AfterMatchSkipStrategy;
import org.apache.flink.cep.pattern.Pattern;
import org.apache.flink.cep.pattern.conditions.SimpleCondition;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.apache.flink.streaming.api.datastream.DataStream;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Slf4j
public class RaceConditionPatternDetector extends AbstractPatternDetector<ClickstreamAnomaly> {
    
    @Override
    public Pattern<Event, ?> definePattern() {
        return Pattern.<Event>begin("addToCart", AfterMatchSkipStrategy.skipPastLastEvent())
                .where(SimpleCondition.of(event -> "add_to_cart".equals(event.getEventType())))
                .followedBy("productView")
                .where(SimpleCondition.of(event -> "product_view".equals(event.getEventType())))
                .within(Time.seconds(10));
    }
    
    @Override
    public ClickstreamAnomaly extractAlert(Map<String, List<Event>> patternMatch) {
        Event addToCartEvent = patternMatch.get("addToCart").get(0);
        Event productViewEvent = patternMatch.get("productView").get(0);
        
        return ClickstreamAnomaly.builder()
                .userId(addToCartEvent.getUserid())
                .severity("medium")
                .description(String.format("Clickstream race condition detected: add_to_cart event (seq: %d) occurred before product_view event (seq: %d) for same user session. Expected user journey violated - cart action without prior product interaction suggests frontend async operation race condition or event tracking system batching issues.",
                        addToCartEvent.getGlobalseq(), productViewEvent.getGlobalseq()))
                .anomalyType("sequence_violation")
                .affectedEvents(Arrays.asList(
                        ClickstreamAnomaly.AffectedEvent.builder()
                                .globalseq(addToCartEvent.getGlobalseq())
                                .eventType(addToCartEvent.getEventType())
                                .timestamp(addToCartEvent.getEventtimestamp())
                                .build(),
                        ClickstreamAnomaly.AffectedEvent.builder()
                                .globalseq(productViewEvent.getGlobalseq())
                                .eventType(productViewEvent.getEventType())
                                .timestamp(productViewEvent.getEventtimestamp())
                                .build()
                ))
                .detectionTimestamp(System.currentTimeMillis())
                .build();
    }
    
    @Override
    public DataStream<ClickstreamAnomaly> detectAnomalies(DataStream<Event> eventStream) {
        return org.apache.flink.cep.CEP.pattern(
                eventStream.keyBy(Event::getUserid),
                definePattern())
                .inEventTime()
                .select(this::extractAlert);
    }
}