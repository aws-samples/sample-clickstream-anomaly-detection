package com.amazonaws.proserve.workshop.suppression;

import com.amazonaws.proserve.workshop.process.model.ClickstreamAnomaly;
import lombok.extern.slf4j.Slf4j;
import org.apache.flink.api.common.state.ValueState;
import org.apache.flink.api.common.state.ValueStateDescriptor;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.functions.KeyedProcessFunction;
import org.apache.flink.util.Collector;

@Slf4j
public class AlertSuppressionFunction extends KeyedProcessFunction<Long, ClickstreamAnomaly, ClickstreamAnomaly> {
    private ValueState<Long> lastAlertTime;
    
    @Override
    public void open(Configuration parameters) {
        lastAlertTime = getRuntimeContext().getState(
            new ValueStateDescriptor<>("lastAlertTime", Long.class));
    }
    
    @Override
    public void processElement(ClickstreamAnomaly alert, Context ctx, Collector<ClickstreamAnomaly> out) throws Exception {
        long currentTime = ctx.timestamp();
        Long lastTime = lastAlertTime.value();
        
        if (lastTime == null || (currentTime - lastTime) >= 120000) {
            lastAlertTime.update(currentTime);
            log.info("Emitting alert for user {} (suppression cleared)", alert.getUserId());
            out.collect(alert);
        } else {
            log.info("Suppressing alert for user {} ({}s remaining)", 
                alert.getUserId(), (120000 - (currentTime - lastTime)) / 1000);
        }
    }
}