/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: MIT-0
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this
 * software and associated documentation files (the "Software"), to deal in the Software
 * without restriction, including without limitation the rights to use, copy, modify,
 * merge, publish, distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED,
 * INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A
 * PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
 * HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION
 * OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.amazonaws.proserve.workshop;

import com.amazonaws.proserve.workshop.pattern.AbstractPatternDetector;
import com.amazonaws.proserve.workshop.pattern.RaceConditionPatternDetector;
import com.amazonaws.proserve.workshop.process.model.ClickstreamAnomaly;
import com.amazonaws.proserve.workshop.process.model.Event;
import com.amazonaws.proserve.workshop.process.model.ConversionMetrics;
import com.amazonaws.proserve.workshop.process.model.ProductMetrics;
import com.amazonaws.proserve.workshop.process.model.HealthMetrics;
import com.amazonaws.proserve.workshop.aggregators.ConversionFunnelAggregator;
import com.amazonaws.proserve.workshop.aggregators.ProductPerformanceAggregator;
import com.amazonaws.proserve.workshop.aggregators.HealthScoreAggregator;
import com.amazonaws.proserve.workshop.serde.JsonDeserializationSchema;
import com.amazonaws.proserve.workshop.serde.JsonSerializationSchema;
import com.amazonaws.proserve.workshop.suppression.AlertSuppressionFunction;
import com.amazonaws.services.kinesisanalytics.runtime.KinesisAnalyticsRuntime;

import lombok.extern.slf4j.Slf4j;

import org.apache.commons.lang3.StringUtils;
import org.apache.flink.connector.kafka.sink.KafkaSink;
import org.apache.flink.connector.kafka.sink.KafkaRecordSerializationSchema;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.streaming.api.datastream.*;
import org.apache.flink.streaming.api.windowing.assigners.EventTimeSessionWindows;
import org.apache.flink.streaming.api.windowing.assigners.SessionWindowTimeGapExtractor;
import org.apache.flink.streaming.api.windowing.assigners.SlidingProcessingTimeWindows;
import org.apache.flink.streaming.api.windowing.assigners.TumblingProcessingTimeWindows;
import org.apache.flink.streaming.api.windowing.time.Time;
import picocli.CommandLine;

import java.io.IOException;
import java.util.Map;
import java.util.Properties;


/**
 * Entry point class. Defines and parses CLI arguments, instantiate top level
 * classes and starts the job.
 */

@CommandLine.Command(name = "FreeThrowPrediction", mixinStandardHelpOptions = true, description = "Predict the outcome of Free Throw event during the game")
@Slf4j
public class AnomalyDetection implements Runnable {
    @CommandLine.Option(names = { "-g", "--config-group" }, description = "Configuration Group")
    private static String propertyGroupId = "AnomalyDetection";

    @CommandLine.Option(names = { "-f", "--config-file" }, description = "Configuration File")
    private static String propertyFile = "";

    public static void main(String[] args) {
        new CommandLine(new AnomalyDetection()).execute(args);
    }

    @Override
    public void run() {
        try {

            Properties jobProps = getProps(propertyGroupId, propertyFile);

            String sourceTopic = getProperty(jobProps, "sourceTopic", "");
            String sourceBootstrapServer = getProperty(jobProps, "sourceBootstrapServer", "");
            String sinkTopic = getProperty(jobProps, "sinkTopic", "");
            String sinkBootstrapServer = getProperty(jobProps, "sinkBootstrapServer", "");
            
            // Business metrics topics
            String conversionTopic = getProperty(jobProps, "conversionMetricsTopic", "business-conversion-metrics");
            String productTopic = getProperty(jobProps, "productMetricsTopic", "business-product-metrics");
            String healthTopic = getProperty(jobProps, "healthMetricsTopic", "business-health-metrics");
            log.info("Flink Job properties map: sourceTopic {} sinkTopic {} sourceBootstrapServer {} sinkBootstrapServer {}", sourceTopic,  sinkTopic, sourceBootstrapServer, sinkBootstrapServer);

            final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

            // Configure Flink web dashboard port for local environment only
            if (env instanceof org.apache.flink.streaming.api.environment.LocalStreamEnvironment) {
                org.apache.flink.configuration.Configuration config = new org.apache.flink.configuration.Configuration();
                
                config.setInteger("rest.port", 53374);
                config.setBoolean("web.submit.enable", true); 
                env.configure(config);                
                System.out.println("Flink Web UI: http://localhost:53374");
                env.setParallelism(6);
            }

            Properties kafkaProps = new Properties();
            kafkaProps.setProperty("security.protocol", "SASL_SSL");
            kafkaProps.setProperty("sasl.mechanism", "AWS_MSK_IAM");
            kafkaProps.setProperty("sasl.jaas.config", "software.amazon.msk.auth.iam.IAMLoginModule required;");
            kafkaProps.setProperty("sasl.client.callback.handler.class",
                    "software.amazon.msk.auth.iam.IAMClientCallbackHandler");


            String initpos = getProperty(jobProps, "initpos", "EARLIEST");
            OffsetsInitializer startingOffsets;
            if ("LATEST".equals(initpos)) {
                startingOffsets = OffsetsInitializer.latest();
            } else if ("EARLIEST".equals(initpos)) {
                startingOffsets = OffsetsInitializer.earliest();
            } else {
                if (StringUtils.isBlank(initpos)) {
                    throw new IllegalArgumentException(
                            "Please set value for initial position to be one of LATEST, EARLIEST or use a timestamp for TIMESTAMP position");
                }
                startingOffsets = OffsetsInitializer.timestamp(Long.parseLong(initpos));
            }

            final KafkaSource<Event> dataSource = KafkaSource.<Event>builder().setProperties(kafkaProps)
                    .setBootstrapServers(sourceBootstrapServer).setGroupId("AnomalyDetectorApp")
                    .setTopics(sourceTopic).setStartingOffsets(startingOffsets)
                    .setValueOnlyDeserializer(JsonDeserializationSchema.forSpecific(Event.class)).build();

            final DataStream<Event> stream = env.fromSource(dataSource,
                    WatermarkStrategy.<Event>forMonotonousTimestamps()
                            .withTimestampAssigner((event, timestamp) -> event.getEventtimestamp()),
                    "Source");

            AbstractPatternDetector<ClickstreamAnomaly> patternDetector = new RaceConditionPatternDetector();
            DataStream<ClickstreamAnomaly> raceConditions = patternDetector.detectAnomalies(stream);

            // Add suppression logic - only allow 1 alert per user every 120 seconds
            DataStream<ClickstreamAnomaly> suppressedAlerts = raceConditions
                .keyBy(ClickstreamAnomaly::getUserId)
                .process(new AlertSuppressionFunction());

            // Create Kafka sink
            KafkaSink<ClickstreamAnomaly> sink = KafkaSink.<ClickstreamAnomaly>builder()
                    .setBootstrapServers(sinkBootstrapServer)
                    .setRecordSerializer(KafkaRecordSerializationSchema.builder()
                            .setTopic(sinkTopic)
                            .setValueSerializationSchema(JsonSerializationSchema.forSpecific(ClickstreamAnomaly.class))
                            .build())
                    .setKafkaProducerConfig(kafkaProps)
                    .build();

            suppressedAlerts.sinkTo(sink).name("Sink");

            // Business Metrics Calculations
            
            // 1. Conversion Funnel Metrics (Session windows with 1 second gap)
            DataStream<ConversionMetrics> conversionMetrics = stream
                .keyBy(Event::getUserid)
                .window(EventTimeSessionWindows.withGap(Time.seconds(1)))
                .process(new ConversionFunnelAggregator());
            
            KafkaSink<ConversionMetrics> conversionSink = KafkaSink.<ConversionMetrics>builder()
                .setBootstrapServers(sinkBootstrapServer)
                .setRecordSerializer(KafkaRecordSerializationSchema.builder()
                    .setTopic(conversionTopic)
                    .setValueSerializationSchema(JsonSerializationSchema.forSpecific(ConversionMetrics.class))
                    .build())
                .setKafkaProducerConfig(kafkaProps)
                .build();
            conversionMetrics.sinkTo(conversionSink).name("ConversionMetricsSink");
            
            // 2. Product Performance Metrics (10-second tumbling window)
            DataStream<ProductMetrics> productMetrics = stream
                .keyBy(Event::getProductType)
                .window(TumblingProcessingTimeWindows.of(Time.seconds(10)))
                .process(new ProductPerformanceAggregator());
            
            KafkaSink<ProductMetrics> productSink = KafkaSink.<ProductMetrics>builder()
                .setBootstrapServers(sinkBootstrapServer)
                .setRecordSerializer(KafkaRecordSerializationSchema.builder()
                    .setTopic(productTopic)
                    .setValueSerializationSchema(JsonSerializationSchema.forSpecific(ProductMetrics.class))
                    .build())
                .setKafkaProducerConfig(kafkaProps)
                .build();
            productMetrics.sinkTo(productSink).name("ProductMetricsSink");
            
            // 3. Health Score Metrics (1-minute tumbling window)
            DataStream<HealthMetrics> healthMetrics = raceConditions
                .keyBy(ClickstreamAnomaly::getUserId)
                .window(SlidingProcessingTimeWindows.of(Time.minutes(1), Time.seconds(1)))
                .process(new HealthScoreAggregator());
            
            KafkaSink<HealthMetrics> healthSink = KafkaSink.<HealthMetrics>builder()
                .setBootstrapServers(sinkBootstrapServer)
                .setRecordSerializer(KafkaRecordSerializationSchema.builder()
                    .setTopic(healthTopic)
                    .setValueSerializationSchema(JsonSerializationSchema.forSpecific(HealthMetrics.class))
                    .build())
                .setKafkaProducerConfig(kafkaProps)
                .build();
            healthMetrics.sinkTo(healthSink).name("HealthMetricsSink");
            
            env.execute("Anomaly Detection");
        } catch (Exception ex) {
            log.error("Failed to initialize job because of exception: {}, stack: {}", ex, ex.getStackTrace());
            throw new RuntimeException(ex);
        }
    }

    protected static Properties getProps(String propertyGroupId, String configFile) throws IOException {
        if (!configFile.isEmpty()) {
            log.debug("Load AppProperties from provided file: {}", configFile);
            Properties props = new Properties();
            try (java.io.FileInputStream fis = new java.io.FileInputStream(configFile)) {
                props.load(fis);
            }
            return props;
        } else {
            Map<String, Properties> appConfigs = KinesisAnalyticsRuntime.getApplicationProperties();
            Properties props = appConfigs.get(propertyGroupId);
            if (props == null || props.isEmpty()) {
                throw new IllegalArgumentException(
                        "No such property group found or group have no properties, group id: " + propertyGroupId);
            }
            return props;
        }
    }

    protected static String getProperty(Properties properties, String name, String defaultValue) {
        String value = properties.getProperty(name);
        if (StringUtils.isBlank(value)) {
            value = defaultValue;
        }
        return value;
    }
}