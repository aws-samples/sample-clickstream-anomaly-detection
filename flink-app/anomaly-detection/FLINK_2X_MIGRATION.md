# Flink 2.x Migration Guide

This document details all changes made to migrate the clickstream anomaly detection application from Flink 1.18.1 to Flink 2.2.0.

## Overview

The migration involved updating dependencies, fixing API changes, and resolving Java compatibility issues. The application now runs on Flink 2.2.0 for standalone deployment (not AWS MSF).

---

## Dependency Changes (pom.xml)

### 1. Core Flink Version
**Changed:** `flink.version` from `1.18.1` to `2.2.0`
**Reason:** Primary upgrade to Flink 2.x

### 2. Java Version
**Changed:** `maven.compiler.source` and `maven.compiler.target` from `11` to `17`
**Reason:** Flink 2.x requires Java 17 minimum

### 3. Kafka Connector
**Changed:** `kafka.version` from `3.1.0-1.18` to `4.0.1-2.0`
**Reason:** Flink 2.x requires compatible Kafka connector version

### 4. AWS Connector Base
**Removed:** `flink-connector-aws-base` dependency (version `4.2.0-1.18`)
**Reason:** Not needed for Kafka-only setup; was causing version conflicts

### 5. MSK IAM Auth
**Changed:** `msk-iam-auth.version` from `1.1.9` to `2.3.5`
**Reason:** Updated to latest version for better compatibility

### 6. AWS SDK
**Changed:** `aws.sdk.version` from `1.12.470` to `1.12.677`
**Reason:** Updated to latest stable version

### 7. Jackson
**Changed:** `jackson-datatype-jsr310` from `2.13.2` to `2.15.2`
**Reason:** Compatibility with Flink 2.x and Java 17

### 8. Lombok
**Changed:** `lombok` from `1.18.30` to `1.18.36`
**Reason:** Version 1.18.30 is not compatible with Java 23 (which was being used by Maven)

### 9. Log4j
**Changed:** Logging dependencies from mixed versions to `2.23.1`
**Before:**
- `slf4j-log4j12` version `1.7.32`
- `log4j-to-slf4j` version `2.17.1`
- `log4j-api` version `2.17.1`

**After:**
- `log4j-slf4j-impl` version `2.23.1`
- `log4j-api` version `2.23.1`
- `log4j-core` version `2.23.1`

**Reason:** Flink 2.x uses log4j-slf4j-impl instead of slf4j-log4j12

### 10. Maven Compiler Plugin
**Changed:** `maven.compiler.plugin.version` from `3.8.1` to `3.11.0`
**Added:** `<release>17</release>` configuration
**Removed:** `<compilerArgs>` with undefined `${compilerArgument}` variable
**Reason:** Better Java 17 support and removed undefined variable causing issues

### 11. AWS Kinesis Analytics Runtime
**Removed:** `aws-kinesisanalytics-runtime` dependency
**Reason:** Not deploying to AWS MSF, so MSF-specific runtime not needed

### 12. Dependency Management
**Added:** AWS SDK BOM in `<dependencyManagement>` section
**Reason:** Better version management across AWS dependencies

### 13. flink-runtime-web Scope
**Changed:** Scope from `provided` (with duplicate in test) to `compile`
**Removed:** Duplicate test-scoped dependency
**Reason:** Fixed duplicate dependency warning and ensured proper inclusion

### 14. flink-streaming-java Scope
**Changed:** Scope from `provided` to `compile` (default)
**Reason:** Required for CEP serialization support in Flink 2.x; must be included in the JAR

### 15. flink-avro Version Conflict
**Added:** `flink-avro` version `2.2.0` as explicit dependency
**Changed:** Excluded old `flink-avro` (1.12.2) from `schema-registry-flink-serde`

**Before:**
```xml
<dependency>
    <groupId>software.amazon.glue</groupId>
    <artifactId>schema-registry-flink-serde</artifactId>
    <version>1.1.15</version>
</dependency>
```

**After:**
```xml
<dependency>
    <groupId>org.apache.flink</groupId>
    <artifactId>flink-avro</artifactId>
    <version>${flink.version}</version>
</dependency>
<dependency>
    <groupId>software.amazon.glue</groupId>
    <artifactId>schema-registry-flink-serde</artifactId>
    <version>1.1.15</version>
    <exclusions>
        <exclusion>
            <groupId>org.apache.flink</groupId>
            <artifactId>flink-avro</artifactId>
        </exclusion>
    </exclusions>
</dependency>
```

**Reason:** Glue Schema Registry library was pulling in Flink 1.x `flink-avro` (1.12.2) which contains references to removed classes like `org.apache.flink.api.java.typeutils.runtime.kryo.Serializers$SpecificInstanceCollectionSerializerForArrayList`. Flink 2.x removed the `flink-java` module and these Kryo serializer classes. Using Flink 2.2.0's `flink-avro` resolves the `NoClassDefFoundError`.

---

## Code Changes

### 1. Time API Migration
**File:** `AnomalyDetection.java`, `RaceConditionPatternDetector.java`

**Changed:** Import from `org.apache.flink.streaming.api.windowing.time.Time` to `java.time.Duration`

**Before:**
```java
import org.apache.flink.streaming.api.windowing.time.Time;
// ...
.window(EventTimeSessionWindows.withGap(Time.seconds(1)))
.window(TumblingProcessingTimeWindows.of(Time.seconds(10)))
.window(SlidingProcessingTimeWindows.of(Time.minutes(1), Time.seconds(1)))
.within(Time.seconds(10))
```

**After:**
```java
import java.time.Duration;
// ...
.window(EventTimeSessionWindows.withGap(Duration.ofSeconds(1)))
.window(TumblingProcessingTimeWindows.of(Duration.ofSeconds(10)))
.window(SlidingProcessingTimeWindows.of(Duration.ofMinutes(1), Duration.ofSeconds(1)))
.within(Duration.ofSeconds(10))
```

**Reason:** Flink 2.x replaced custom `Time` class with Java's standard `Duration` class

### 2. Configuration API Changes
**File:** `AnomalyDetection.java`

**Before:**
```java
config.setInteger("rest.port", 53374);
config.setBoolean("web.submit.enable", true);
```

**After:**
```java
config.set(org.apache.flink.configuration.RestOptions.PORT, 53374);
config.set(org.apache.flink.configuration.WebOptions.SUBMIT_ENABLE, true);
```

**Reason:** Flink 2.x changed Configuration API from string-based to type-safe ConfigOption-based

### 3. KeyedProcessFunction.open() Method Signature
**File:** `AlertSuppressionFunction.java`

**Before:**
```java
@Override
public void open(Configuration parameters) {
    lastAlertTime = getRuntimeContext().getState(
        new ValueStateDescriptor<>("lastAlertTime", Long.class));
}
```

**After:**
```java
@Override
public void open(org.apache.flink.api.common.functions.OpenContext openContext) throws Exception {
    lastAlertTime = getRuntimeContext().getState(
        new ValueStateDescriptor<>("lastAlertTime", Long.class));
}
```

**Reason:** Flink 2.x changed the `open()` method signature to use `OpenContext` instead of `Configuration`

### 4. Removed MSF Runtime Usage
**File:** `AnomalyDetection.java`

**Removed:** Import and usage of `KinesisAnalyticsRuntime`

**Before:**
```java
import com.amazonaws.services.kinesisanalytics.runtime.KinesisAnalyticsRuntime;
// ...
Map<String, Properties> appConfigs = KinesisAnalyticsRuntime.getApplicationProperties();
Properties props = appConfigs.get(propertyGroupId);
```

**After:**
```java
// Removed import
// ...
throw new IllegalArgumentException(
    "Configuration file must be provided via -f or --config-file parameter");
```

**Reason:** Not deploying to AWS MSF, so removed MSF-specific runtime dependency

### 5. Removed Unused Imports
**Files:** `AlertSuppressionFunction.java`, `AnomalyDetection.java`

**Removed:**
- `org.apache.flink.configuration.Configuration` (AlertSuppressionFunction)
- `com.amazonaws.proserve.workshop.serde.JsonDeserializationSchema` (AnomalyDetection)

**Reason:** Cleanup after API changes

### 6. CEP Pattern Serialization
**File:** `RaceConditionPatternDetector.java`

**Added:** Type information hint for CEP pattern output

**Before:**
```java
return org.apache.flink.cep.CEP.pattern(
        eventStream.keyBy((event) -> String.format("%s-%s", event.getUserid(), event.getProductType().toString())),
        definePattern())
        .inEventTime()
        .select(this::extractAlert);
```

**After:**
```java
import org.apache.flink.api.common.typeinfo.TypeHint;
import org.apache.flink.api.common.typeinfo.TypeInformation;
// ...
return org.apache.flink.cep.CEP.pattern(
        eventStream.keyBy((event) -> String.format("%s-%s", event.getUserid(), event.getProductType().toString())),
        definePattern())
        .inEventTime()
        .select(
            this::extractAlert,
            TypeInformation.of(new TypeHint<ClickstreamAnomaly>() {})
        );
```

**Reason:** Flink 2.x CEP requires explicit type information for proper serialization

### 7. Event Model Serialization
**File:** `Event.java`

**Added:** Serializable interface, serialVersionUID, and no-args constructor

**Before:**
```java
@Data
@Builder
@Jacksonized
@ToString
public class Event {
    // fields...
}
```

**After:**
```java
@Data
@Builder
@Jacksonized
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class Event implements Serializable {
    private static final long serialVersionUID = 1L;
    // fields...
}
```

**Reason:** Flink 2.x requires POJOs to be properly serializable with no-args constructor for efficient serialization without Kryo fallback

---

## Build Process Changes

### Java Version Requirement
**Issue:** Maven was using Java 23 by default, but Lombok 1.18.30 wasn't compatible
**Solution:** Set `JAVA_HOME` to Java 17 before building

**Build Command:**
```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 17) && mvn clean package -DskipTests
```

**Reason:** Ensures Maven uses Java 17 compiler, which is compatible with all dependencies

---

## Summary of Breaking Changes

1. **Time API:** All `Time.*` calls must be replaced with `Duration.of*()` calls
2. **Configuration API:** String-based config setters replaced with type-safe `ConfigOption` setters
3. **Function Lifecycle:** `open(Configuration)` changed to `open(OpenContext)`
4. **Java Version:** Minimum Java version increased from 11 to 17
5. **Connector Versions:** All Flink connectors must use 2.x compatible versions
6. **Logging:** Log4j bridge changed from `slf4j-log4j12` to `log4j-slf4j-impl`
7. **Avro Serialization:** Must use Flink 2.x `flink-avro` and exclude old versions from transitive dependencies
8. **CEP Serialization:** Requires explicit TypeInformation hints for pattern output types
9. **POJO Requirements:** Model classes must implement Serializable with no-args constructor

---

## Local Testing

A complete Docker-based local testing environment is available in the `local-testing/` directory:

```bash
cd flink-app/anomaly-detection/local-testing
./start-local-env.sh      # Start Kafka + Flink
./deploy-flink-job.sh     # Deploy the application
./send-test-events.sh     # Send test events
./monitor-anomalies.sh    # Watch for anomalies
./stop-local-env.sh       # Stop environment
```

See `local-testing/LOCAL_TESTING.md` for detailed documentation.

The local environment uses:
- JSON serialization (not Avro) for simplicity
- PLAINTEXT security (no authentication)
- Flink 2.2.0 with Java 17
- Kafka with auto-created topics

## Testing

After migration, the application successfully:
- Compiles and packages: `target/clickstream-anomaly-detection-2.0-SNAPSHOT.jar`
- Deploys to Flink 2.2.0 cluster (Docker-based local environment)
- Runs with Java 17
- Build Status: SUCCESS
- Runtime Status: Job submitted successfully (JobID: 835285dc5c598625c3970096d0bfbc02)

### Local Testing Environment
Created Docker Compose setup with:
- Flink 2.2.0 (JobManager + TaskManager) with Java 17
- Kafka 7.5.0
- Zookeeper
- Schema Registry

See `LOCAL_TESTING.md` for complete testing guide.

---

## References

- Flink 2.2 Release Notes: https://flink.apache.org/
- Flink Migration Guide: https://nightlies.apache.org/flink/flink-docs-master/
- Kafka Connector Compatibility: https://nightlies.apache.org/flink/flink-docs-master/docs/connectors/datastream/kafka/
