#!/bin/bash

set -e

echo "=========================================="
echo "Starting Local Flink + Kafka Environment"
echo "=========================================="

# Check if JAR exists
if [ ! -f "../target/clickstream-anomaly-detection-2.0-SNAPSHOT.jar" ]; then
    echo "ERROR: JAR file not found. Building application..."
    cd ..
    export JAVA_HOME=$(/usr/libexec/java_home -v 17)
    mvn clean package -DskipTests
    cd local-testing
fi

# Start Docker Compose
echo ""
echo "Starting Docker containers..."
docker-compose up -d

# Wait for services to be ready
echo ""
echo "Waiting for services to start..."
sleep 10

# Check Kafka
echo ""
echo "Checking Kafka..."
docker exec kafka kafka-topics --bootstrap-server kafka:9092 --list || echo "Kafka not ready yet, waiting..."
sleep 5

# Create Kafka topics
echo ""
echo "Creating Kafka topics..."
docker exec kafka kafka-topics --bootstrap-server kafka:9092 --create --if-not-exists --topic clickstream-events --partitions 3 --replication-factor 1
docker exec kafka kafka-topics --bootstrap-server kafka:9092 --create --if-not-exists --topic clickstream-anomalies --partitions 3 --replication-factor 1
docker exec kafka kafka-topics --bootstrap-server kafka:9092 --create --if-not-exists --topic conversion-metrics --partitions 3 --replication-factor 1
docker exec kafka kafka-topics --bootstrap-server kafka:9092 --create --if-not-exists --topic product-metrics --partitions 3 --replication-factor 1
docker exec kafka kafka-topics --bootstrap-server kafka:9092 --create --if-not-exists --topic health-metrics --partitions 3 --replication-factor 1

echo ""
echo "Topics created:"
docker exec kafka kafka-topics --bootstrap-server kafka:9092 --list

echo ""
echo "=========================================="
echo "Environment is ready!"
echo "=========================================="
echo ""
echo "Services:"
echo "  - Flink Web UI: http://localhost:8081"
echo "  - Kafka: localhost:9092"
echo "  - Schema Registry: http://localhost:8082"
echo ""
echo "Next steps:"
echo "  1. Deploy the Flink job: ./deploy-flink-job.sh"
echo "  2. Send test events: ./send-test-events.sh"
echo "  3. Monitor anomalies: ./monitor-anomalies.sh"
echo ""
