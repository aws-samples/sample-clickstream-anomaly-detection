#!/bin/bash

echo "=========================================="
echo "Stopping Local Flink + Kafka Environment"
echo "=========================================="

docker-compose down

echo ""
echo "Environment stopped successfully!"
echo ""
echo "To remove all data volumes, run: docker-compose down -v"
echo ""
