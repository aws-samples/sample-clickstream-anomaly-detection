#!/bin/bash

set -e

echo "=========================================="
echo "Deploying Flink Job"
echo "=========================================="

JAR_FILE="../target/clickstream-anomaly-detection-2.0-SNAPSHOT.jar"
CONFIG_FILE="config/application.properties"

# Check if JAR exists
if [ ! -f "$JAR_FILE" ]; then
    echo "ERROR: JAR file not found at $JAR_FILE"
    echo "Please build the application first: mvn clean package -DskipTests"
    exit 1
fi

# Check if Flink is running
if ! docker ps | grep -q jobmanager; then
    echo "ERROR: Flink JobManager is not running"
    echo "Please start the environment first: ./start-local-env.sh"
    exit 1
fi

# JAR is already in target directory which is mounted to /opt/flink/usrlib
echo ""
echo "JAR file ready at: $JAR_FILE"

# Copy config to container
echo "Copying config to Flink JobManager..."
docker cp "$CONFIG_FILE" jobmanager:/opt/flink/conf/application.properties

# Submit job
echo ""
echo "Submitting Flink job..."
docker exec jobmanager /opt/flink/bin/flink run \
    -d \
    /opt/flink/usrlib/clickstream-anomaly-detection-2.0-SNAPSHOT.jar \
    --config-file /opt/flink/conf/application.properties

echo ""
echo "=========================================="
echo "Job submitted successfully!"
echo "=========================================="
echo ""
echo "View job status at: http://localhost:8081"
echo ""
