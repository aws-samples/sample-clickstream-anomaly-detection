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
import com.amazonaws.clickstream.ClickstreamEvent;
import com.amazonaws.services.schemaregistry.flink.avro.GlueSchemaRegistryAvroDeserializationSchema;
import com.amazonaws.services.schemaregistry.utils.AWSSchemaRegistryConstants;
import com.amazonaws.services.schemaregistry.utils.AvroRecordType;
import org.apache.flink.connector.kafka.source.reader.deserializer.KafkaRecordDeserializationSchema;
import org.apache.flink.api.common.serialization.DeserializationSchema;
import com.amazonaws.proserve.workshop.suppression.AlertSuppressionFunction;

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
import org.apache.flink.streaming.api.windowing.assigners.SlidingProcessingTimeWindows;
import org.apache.flink.streaming.api.windowing.assigners.TumblingProcessingTimeWindows;
import picocli.CommandLine;

import java.io.IOException;
import java.time.Duration;
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
            
            // Schema Registry properties
            String awsRegion = getProperty(jobProps, "awsRegion", "");
            String registryName = getProperty(jobProps, "registryName", "");
            String schemaName = getProperty(jobProps, "schemaName", "");
            
            // Business metrics topics
            String conversionTopic = getProperty(jobProps, "conversionMetricsTopic", "");
            String productTopic = getProperty(jobProps, "productMetricsTopic", "");
            String healthTopic = getProperty(jobProps, "healthMetricsTopic", "");
            log.info("Flink Job properties map: sourceTopic {} sinkTopic {} sourceBootstrapServer {} sinkBootstrapServer {} awsRegion {} registryName {} schemaName {}", sourceTopic, sinkTopic, sourceBootstrapServer, sinkBootstrapServer, awsRegion, registryName, schemaName);

            // Get security protocol for Kafka configuration
            String securityProtocol = getProperty(jobProps, "securityProtocol", "PLAINTEXT");
            
            // Create topics if they don't exist
            createTopicsIfNotExist(sourceBootstrapServer, securityProtocol, 
                sourceTopic, sinkTopic, conversionTopic, productTopic, healthTopic);

            final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

            // Configure Flink web dashboard port for local environment only
            if (env instanceof org.apache.flink.streaming.api.environment.LocalStreamEnvironment) {
                org.apache.flink.configuration.Configuration config = new org.apache.flink.configuration.Configuration();
                
                config.set(org.apache.flink.configuration.RestOptions.PORT, 53374);
                config.set(org.apache.flink.configuration.WebOptions.SUBMIT_ENABLE, true); 
                env.configure(config);                
                System.out.println("Flink Web UI: http://localhost:53374");
                env.setParallelism(6);
            }

            Properties kafkaProps = new Properties();
            
            // Use PLAINTEXT for local Kafka, SASL_SSL for MSK
            if ("SASL_SSL".equals(securityProtocol)) {
                kafkaProps.setProperty("security.protocol", "SASL_SSL");
                kafkaProps.setProperty("sasl.mechanism", "AWS_MSK_IAM");
                kafkaProps.setProperty("sasl.jaas.config", "software.amazon.msk.auth.iam.IAMLoginModule required;");
                kafkaProps.setProperty("sasl.client.callback.handler.class",
                        "software.amazon.msk.auth.iam.IAMClientCallbackHandler");
            } else {
                kafkaProps.setProperty("security.protocol", "PLAINTEXT");
            }


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

            // Support both JSON (for local testing) and Avro (for production with Glue Schema Registry)
            String serializationFormat = getProperty(jobProps, "serializationFormat", "JSON");
            KafkaRecordDeserializationSchema<ClickstreamEvent> kafkaRecordDeserializationSchema;
            
            if ("AVRO".equalsIgnoreCase(serializationFormat)) {
                Map<String, Object> deserializerConfig = Map.of(
                        AWSSchemaRegistryConstants.AVRO_RECORD_TYPE, AvroRecordType.SPECIFIC_RECORD.getName(),
                        AWSSchemaRegistryConstants.AWS_REGION, awsRegion,
                        AWSSchemaRegistryConstants.REGISTRY_NAME, registryName,
                        AWSSchemaRegistryConstants.SCHEMA_NAME, schemaName);
                
                DeserializationSchema<ClickstreamEvent> avroDeserializationSchema = 
                        GlueSchemaRegistryAvroDeserializationSchema.forSpecific(ClickstreamEvent.class, deserializerConfig);
                
                kafkaRecordDeserializationSchema = 
                        KafkaRecordDeserializationSchema.valueOnly(avroDeserializationSchema);
            } else {
                // Use JSON deserialization for local testing
                DeserializationSchema<ClickstreamEvent> jsonDeserializationSchema = 
                        com.amazonaws.proserve.workshop.serde.JsonDeserializationSchema.forSpecific(ClickstreamEvent.class);
                
                kafkaRecordDeserializationSchema = 
                        KafkaRecordDeserializationSchema.valueOnly(jsonDeserializationSchema);
            }

            final KafkaSource<ClickstreamEvent> avroDataSource = KafkaSource.<ClickstreamEvent>builder()
                    .setProperties(kafkaProps)
                    .setBootstrapServers(sourceBootstrapServer)
                    .setGroupId("AnomalyDetectorApp")
                    .setTopics(sourceTopic)
                    .setStartingOffsets(startingOffsets)
                    .setDeserializer(kafkaRecordDeserializationSchema)
                    .build();

            final DataStream<Event> stream = env.fromSource(avroDataSource,
                    WatermarkStrategy.<ClickstreamEvent>forBoundedOutOfOrderness(Duration.ofMillis(500))
                            .withTimestampAssigner((event, timestamp) -> event.getEventtimestamp()),
                    "AvroSource")
                    .map(clickstreamEvent -> Event.builder()
                            .userid(clickstreamEvent.getUserid())
                            .globalseq(clickstreamEvent.getGlobalseq())
                            .eventType(clickstreamEvent.getEventType() != null ? clickstreamEvent.getEventType().toString() : null)
                            .productType(clickstreamEvent.getProductType() != null ? clickstreamEvent.getProductType().toString() : null)
                            .eventtimestamp(clickstreamEvent.getEventtimestamp())
                            .prevglobalseq(clickstreamEvent.getPrevglobalseq())
                            .build());

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
                .window(EventTimeSessionWindows.withGap(Duration.ofSeconds(1)))
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
                .window(TumblingProcessingTimeWindows.of(Duration.ofSeconds(10)))
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
                .window(SlidingProcessingTimeWindows.of(Duration.ofMinutes(1), Duration.ofSeconds(1)))
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
            throw new IllegalArgumentException(
                    "Configuration file must be provided via -f or --config-file parameter");
        }
    }

    protected static String getProperty(Properties properties, String name, String defaultValue) {
        String value = properties.getProperty(name);
        if (StringUtils.isBlank(value)) {
            value = defaultValue;
        }
        return value;
    }

    private static void createTopicsIfNotExist(String bootstrapServers, String securityProtocol, String... topics) {
        Properties adminProps = new Properties();
        adminProps.put("bootstrap.servers", bootstrapServers);
        
        if ("SASL_SSL".equals(securityProtocol)) {
            adminProps.put("security.protocol", "SASL_SSL");
            adminProps.put("sasl.mechanism", "AWS_MSK_IAM");
            adminProps.put("sasl.jaas.config", "software.amazon.msk.auth.iam.IAMLoginModule required;");
            adminProps.put("sasl.client.callback.handler.class", "software.amazon.msk.auth.iam.IAMClientCallbackHandler");
        }
        
        try (org.apache.kafka.clients.admin.AdminClient adminClient = org.apache.kafka.clients.admin.AdminClient.create(adminProps)) {
            java.util.Set<String> existingTopics = adminClient.listTopics().names().get();
            java.util.List<org.apache.kafka.clients.admin.NewTopic> newTopics = new java.util.ArrayList<>();
            
            for (String topic : topics) {
                if (!existingTopics.contains(topic)) {
                    log.info("Creating topic: {}", topic);
                    newTopics.add(new org.apache.kafka.clients.admin.NewTopic(topic, 1, (short) 1));
                } else {
                    log.info("Topic already exists: {}", topic);
                }
            }
            
            if (!newTopics.isEmpty()) {
                adminClient.createTopics(newTopics).all().get();
                log.info("Successfully created {} topics", newTopics.size());
            }
        } catch (Exception e) {
            log.error("Failed to create topics", e);
            throw new RuntimeException("Failed to create Kafka topics", e);
        }
    }
}
