# Local Testing Guide

This guide explains how to test the Flink anomaly detection application locally using Docker Compose.

## Prerequisites

- Docker and Docker Compose installed
- Java 17 installed
- Maven installed
- Application built from parent directory: `cd .. && export JAVA_HOME=$(/usr/libexec/java_home -v 17) && mvn clean package -DskipTests && cd local-testing`

## Architecture

The local environment includes:
- **Kafka** (port 9092): Message broker for event streaming
- **Zookeeper** (port 2181): Kafka coordination service
- **Flink JobManager** (port 8081): Flink cluster coordinator with Web UI
- **Flink TaskManager**: Flink worker node
- **Schema Registry** (port 8082): Avro schema management

## Quick Start

All commands should be run from the `local-testing` directory:

```bash
cd flink-app/anomaly-detection/local-testing
```

### 1. Start the Environment

```bash
./start-local-env.sh
```

This script will:
- Build the application if needed (from parent directory)
- Start all Docker containers
- Create required Kafka topics
- Display service URLs

### 2. Deploy the Flink Job

```bash
./deploy-flink-job.sh
```

This submits the anomaly detection job to the Flink cluster.

### 3. Send Test Events

```bash
./send-test-events.sh
```

This sends both normal and anomalous clickstream events to Kafka.

### 4. Monitor Anomalies

```bash
./monitor-anomalies.sh
```

This displays detected anomalies in real-time. Press Ctrl+C to stop.

### 5. Stop the Environment

```bash
./stop-local-env.sh
```

## Services

### Flink Web UI
- URL: http://localhost:8081
- View running jobs, task managers, and job metrics

### Kafka Topics
- `clickstream-events`: Input events
- `clickstream-anomalies`: Detected anomalies
- `conversion-metrics`: Conversion funnel metrics
- `product-metrics`: Product performance metrics
- `health-metrics`: System health metrics

## Manual Testing

### View Kafka Topics
```bash
docker exec kafka kafka-topics --bootstrap-server localhost:9093 --list
```

### Consume from a Topic
```bash
docker exec -it kafka kafka-console-consumer \
    --bootstrap-server localhost:9093 \
    --topic clickstream-events \
    --from-beginning
```

### Produce to a Topic
```bash
docker exec -it kafka kafka-console-producer \
    --bootstrap-server localhost:9093 \
    --topic clickstream-events
```

Then type JSON events:
```json
{"userid": "test1", "globalseq": 1, "eventType": "product_view", "productType": "electronics", "eventtimestamp": 1234567890000, "prevglobalseq": 0}
```

### Check Flink Job Status
```bash
docker exec jobmanager /opt/flink/bin/flink list
```

### Cancel a Flink Job
```bash
docker exec jobmanager /opt/flink/bin/flink cancel <job-id>
```

### View Flink Logs
```bash
docker logs -f jobmanager
docker logs -f taskmanager
```

## Test Scenarios

### Normal Event Flow
1. `product_view` → User views a product
2. `add_to_cart` → User adds product to cart
3. `checkout` → User completes purchase

**Expected:** No anomalies detected

### Anomalous Event Flow (Race Condition)
1. `add_to_cart` → User adds to cart WITHOUT viewing product first
2. `product_view` → Product view happens AFTER add to cart

**Expected:** Anomaly detected with description about race condition

## Troubleshooting

### Containers won't start
```bash
cd flink-app/anomaly-detection/local-testing
docker-compose down -v
docker-compose up -d
```

### Kafka not ready
Wait 30 seconds after starting, then check:
```bash
docker logs kafka
```

### Flink job fails
Check logs:
```bash
docker logs jobmanager
docker logs taskmanager
```

### Port conflicts
If ports 8081, 9092, or 2181 are in use, modify `docker-compose.yml` to use different ports.

## Configuration

Edit `config/application.properties` to modify:
- Kafka topics
- Bootstrap servers
- Consumer settings
- AWS region (for schema registry)

## Notes

- The application uses PLAINTEXT security (no authentication) for local testing
- MSK IAM authentication is disabled in local mode
- Schema Registry is available but the app currently uses JSON serialization
- All data is ephemeral and will be lost when containers are stopped

## Clean Up

Remove all containers and volumes:
```bash
cd flink-app/anomaly-detection/local-testing
docker-compose down -v
```
