#!/bin/bash

set -e

echo "=========================================="
echo "Sending Test Events to Kafka"
echo "=========================================="

# Check if Kafka is running
if ! docker ps | grep -q kafka; then
    echo "ERROR: Kafka is not running"
    echo "Please start the environment first: ./start-local-env.sh"
    exit 1
fi

echo ""
echo "Sending normal clickstream events..."

# Send normal events (product_view -> add_to_cart -> checkout)
for i in {1..5}; do
    # Product view event
    echo "{\"userid\": $i, \"globalseq\": $((i*3-2)), \"event_type\": \"product_view\", \"product_type\": \"electronics\", \"eventtimestamp\": $(date +%s)000, \"prevglobalseq\": $((i*3-3))}" | \
        docker exec -i kafka kafka-console-producer --bootstrap-server kafka:9093 --topic clickstream-events
    
    # Add to cart event
    echo "{\"userid\": $i, \"globalseq\": $((i*3-1)), \"event_type\": \"add_to_cart\", \"product_type\": \"electronics\", \"eventtimestamp\": $(date +%s)000, \"prevglobalseq\": $((i*3-2))}" | \
        docker exec -i kafka kafka-console-producer --bootstrap-server kafka:9093 --topic clickstream-events
    
    # Checkout event
    echo "{\"userid\": $i, \"globalseq\": $((i*3)), \"event_type\": \"checkout\", \"product_type\": \"electronics\", \"eventtimestamp\": $(date +%s)000, \"prevglobalseq\": $((i*3-1))}" | \
        docker exec -i kafka kafka-console-producer --bootstrap-server kafka:9093 --topic clickstream-events
    
    echo "Sent normal event sequence for user $i"
    sleep 1
done

echo ""
echo "Sending anomalous events (race condition: add_to_cart before product_view)..."

# Send anomalous events (add_to_cart -> product_view)
for i in {6..8}; do
    # Add to cart BEFORE product view (anomaly!)
    echo "{\"userid\": $i, \"globalseq\": $((i*3-2)), \"event_type\": \"add_to_cart\", \"product_type\": \"electronics\", \"eventtimestamp\": $(date +%s)000, \"prevglobalseq\": $((i*3-3))}" | \
        docker exec -i kafka kafka-console-producer --bootstrap-server kafka:9093 --topic clickstream-events
    
    sleep 1
    
    # Product view AFTER add to cart (anomaly!)
    echo "{\"userid\": $i, \"globalseq\": $((i*3-1)), \"event_type\": \"product_view\", \"product_type\": \"electronics\", \"eventtimestamp\": $(date +%s)000, \"prevglobalseq\": $((i*3-2))}" | \
        docker exec -i kafka kafka-console-producer --bootstrap-server kafka:9093 --topic clickstream-events
    
    echo "Sent anomalous event sequence for user $i (race condition)"
    sleep 1
done

echo ""
echo "=========================================="
echo "Test events sent successfully!"
echo "=========================================="
echo ""
echo "Monitor anomalies with: ./monitor-anomalies.sh"
echo ""
