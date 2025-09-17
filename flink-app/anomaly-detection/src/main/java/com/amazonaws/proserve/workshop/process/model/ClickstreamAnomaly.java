/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: MIT-0
 */

package com.amazonaws.proserve.workshop.process.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;
import lombok.ToString;
import lombok.extern.jackson.Jacksonized;

import java.util.List;

@Data
@Builder
@Jacksonized
@ToString
public class ClickstreamAnomaly {
    @JsonProperty("user_id")
    private Long userId;
    
    @JsonProperty("severity")
    private String severity;
    
    @JsonProperty("description")
    private String description;
    
    @JsonProperty("anomaly_type")
    private String anomalyType;
    
    @JsonProperty("affected_events")
    private List<AffectedEvent> affectedEvents;
    
    @JsonProperty("detection_timestamp")
    private Long detectionTimestamp;
    
    @Data
    @Builder
    @Jacksonized
    @ToString
    public static class AffectedEvent {
        @JsonProperty("globalseq")
        private Long globalseq;
        
        @JsonProperty("event_type")
        private String eventType;
        
        @JsonProperty("timestamp")
        private Long timestamp;
    }
}