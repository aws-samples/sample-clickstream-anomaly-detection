package com.amazonaws.proserve.workshop.process.model;

import java.io.Serializable;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Data
@Builder
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class Event implements Serializable {
    private static final long serialVersionUID = 1L;
    
    @JsonProperty("userid")
    private Long userid;
    @JsonProperty("globalseq")
    private Long globalseq;
    @JsonProperty("event_type")
    private String eventType;
    @JsonProperty("product_type")
    private String productType;
    @JsonProperty("eventtimestamp")
    private Long eventtimestamp;
    @JsonProperty("prevglobalseq")
    private Long prevglobalseq;
    @JsonProperty("ingestion_timestamp")
    private Long ingestionTimestamp;

}
