#!/bin/bash
echo "Testing AWS STS GetCallerIdentity..."
aws sts get-caller-identity || echo "STS call failed - check IAM permissions"

echo "Starting DNS troubleshooting..."
echo "BOOTSTRAP_SERVER: ${BOOTSTRAP_SERVER}"
echo "TOPIC_NAME: ${TOPIC_NAME}"
echo "AWS_REGION: ${AWS_REGION}"
echo "SCHEMA_REGISTRY_NAME: ${SCHEMA_REGISTRY_NAME}"
echo "SCHEMA_NAME: ${SCHEMA_NAME}"

if [ -n "$BOOTSTRAP_SERVER" ]; then
    BROKER_HOST=${BOOTSTRAP_SERVER%:*}
    echo "Running nslookup on $BROKER_HOST"
    nslookup $BROKER_HOST
    echo "DNS lookup completed"
else
    echo "BOOTSTRAP_SERVER environment variable not set"
fi

echo "Starting producer with environment variables..."
export BOOTSTRAP_SERVER="${BOOTSTRAP_SERVER}"
export TOPIC_NAME="${TOPIC_NAME}"
export AWS_REGION="${AWS_REGION}"
export SCHEMA_REGISTRY_NAME="${SCHEMA_REGISTRY_NAME:-clickstream-registry}"
export SCHEMA_NAME="${SCHEMA_NAME:-clickstream-event-schema}"

python3 ./normal_events_producer.py