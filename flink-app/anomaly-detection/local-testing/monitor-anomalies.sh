#!/bin/bash

echo "=========================================="
echo "Monitoring Anomalies from Kafka"
echo "=========================================="
echo ""
echo "Listening to clickstream-anomalies topic..."
echo "Press Ctrl+C to stop"
echo ""

docker exec -it kafka kafka-console-consumer \
    --bootstrap-server localhost:9093 \
    --topic clickstream-anomalies \
    --from-beginning \
    --property print.timestamp=true \
    --property print.key=true




docker exec -it kafka kafka-console-consumer \
    --bootstrap-server kafka:9092 \
    --topic clickstream-events \
    --from-beginning \
    --property print.timestamp=true \
    --property print.key=true


echo '{"test": "message"}' | docker exec -i kafka kafka-console-producer --bootstrap-server kafka:9093 --topic health-metrics && sleep 2 && docker exec kafka kafka-console-consumer --bootstrap-server kafka:9093 --topic health-metrics --from-beginning --max-messages 1 --timeout-ms 3000 2>/dev/null
