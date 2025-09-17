# License: MIT-0

import json
import random
import time
import logging
import socket
import os

from kafka import KafkaProducer
from kafka.errors import KafkaError
from aws_msk_iam_sasl_signer import MSKAuthTokenProvider
import data_generator

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

class MSKTokenProvider:
    def token(self):
        token, _ = MSKAuthTokenProvider.generate_auth_token(
            os.environ["AWS_REGION"])
        return token


def produce_normal_events():
    """Produce normal traffic events continuously"""
    # Log environment variables
    print(f"BOOTSTRAP_SERVER: {os.environ.get('BOOTSTRAP_SERVER', 'NOT_SET')}")
    print(f"TOPIC_NAME: {os.environ.get('TOPIC_NAME', 'NOT_SET')}")
    print(f"AWS_REGION: {os.environ.get('AWS_REGION', 'NOT_SET')}")
    
    tp = MSKTokenProvider()
    
    # Log producer configuration
    producer_config = {
        "bootstrap_servers": os.environ["BOOTSTRAP_SERVER"],
        "security_protocol": "SASL_SSL",
        "sasl_mechanism": "OAUTHBEARER",
        "client_id": socket.gethostname()
    }
    print(f"Kafka Producer Config: {producer_config}")
    
    producer = KafkaProducer(
        bootstrap_servers=os.environ["BOOTSTRAP_SERVER"],
        security_protocol="SASL_SSL",
        sasl_mechanism="OAUTHBEARER",
        sasl_oauth_token_provider=tp,
        client_id=socket.gethostname(),
        key_serializer=lambda key: key.encode("utf-8"),
        value_serializer=lambda value: json.dumps(value).encode("utf-8"),
        linger_ms=10,
        batch_size=262144,
    )

    topic = os.environ["TOPIC_NAME"]
    start_time = time.time()
    
    while True:
        current_time = time.time()
        elapsed_time = current_time - start_time
        
        # Alternate every minute: even minutes = normal, odd minutes = anomaly
        minute_number = int(elapsed_time // 120)
        is_anomaly = minute_number % 2 == 1
        
        # Generate events using the main function from data_generator
        event_config = {"ANOMALY": is_anomaly}
        result = data_generator.main(event_config)
        
        # Extract events from the result (assuming main returns events)
        if isinstance(result, dict) and 'events' in result:
            events = result['events']
        else:
            # Fallback: generate a simple event
            events = [{
                'userid': random.randint(10000, 99999),
                'globalseq': random.randint(1000, 9999),
                'event_type': 'product_view',
                'product_type': 'electronics',
                'eventtimestamp': int(time.time() * 1000)
            }]
        
        # Send each event
        for data in events:
            producer.send(topic, key=str(random.randint(1, 10000)), value=data)


if __name__ == "__main__":
    produce_normal_events()
