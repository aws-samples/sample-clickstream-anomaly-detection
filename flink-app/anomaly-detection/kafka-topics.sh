./kafka-topics.sh \
    --create \
    --topic business-conversion-metrics \
    --partitions 12 \
    --bootstrap-server "${SOURCE_BROKERS}" \
    --command-config client.properties

./kafka-topics.sh \
    --create \
    --topic business-product-metrics \
    --partitions 12 \
    --bootstrap-server "${SOURCE_BROKERS}" \
    --command-config client.properties

./kafka-topics.sh \
    --create \
    --topic business-health-metrics \
    --partitions 12 \
    --bootstrap-server "${SOURCE_BROKERS}" \
    --command-config client.properties


./kafka-console-consumer.sh \
    --topic business-conversion-metrics \
    --bootstrap-server "${SOURCE_BROKERS}" \
    --consumer.config client.properties

./kafka-console-consumer.sh \
    --topic business-product-metrics \
    --bootstrap-server "${SOURCE_BROKERS}" \
    --consumer.config client.properties

./kafka-console-consumer.sh \
    --topic business-health-metrics \
    --bootstrap-server "${SOURCE_BROKERS}" \
    --consumer.config client.properties