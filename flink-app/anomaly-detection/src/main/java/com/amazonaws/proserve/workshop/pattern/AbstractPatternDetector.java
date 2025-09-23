package com.amazonaws.proserve.workshop.pattern;

import com.amazonaws.proserve.workshop.process.model.Event;
import org.apache.flink.cep.pattern.Pattern;
import org.apache.flink.streaming.api.datastream.DataStream;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

public abstract class AbstractPatternDetector<T> implements Serializable {
    
    public abstract Pattern<Event, ?> definePattern();
    
    public abstract T extractAlert(Map<String, List<Event>> patternMatch);
    
    public abstract DataStream<T> detectAnomalies(DataStream<Event> eventStream);
}