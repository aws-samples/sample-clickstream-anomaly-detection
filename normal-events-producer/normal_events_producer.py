# License: MIT-0

import json
import random
import time
import logging
import socket
import os
import avro.schema
import avro.io
import io
import boto3
from uuid import UUID

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

class AvroSerializer:
    def __init__(self, registry_name, schema_name):
        self.glue_client = boto3.client('glue', region_name=os.environ["AWS_REGION"])
        self.registry_name = registry_name
        self.schema_name = schema_name
        
        # Load Avro schema
        with open('clickstream-event.avsc', 'r') as f:
            self.schema_str = f.read()
        self.avro_schema = avro.schema.parse(self.schema_str)
        
        # Register schema and get version UUID
        self.schema_version_id = self._register_schema()
        
    def _register_schema(self):
        try:
            # Try to create registry first
            try:
                self.glue_client.create_registry(RegistryName=self.registry_name)
            except self.glue_client.exceptions.AlreadyExistsException:
                pass
            
            # Try to create schema first
            try:
                response = self.glue_client.create_schema(
                    RegistryId={'RegistryName': self.registry_name},
                    SchemaName=self.schema_name,
                    DataFormat='AVRO',
                    Compatibility='BACKWARD',
                    SchemaDefinition=self.schema_str
                )
                return response['SchemaVersionId']
            except self.glue_client.exceptions.AlreadyExistsException:
                # Schema exists, check if this definition already exists
                try:
                    response = self.glue_client.get_schema_by_definition(
                        SchemaId={'RegistryName': self.registry_name, 'SchemaName': self.schema_name},
                        SchemaDefinition=self.schema_str
                    )
                    return response['SchemaVersionId']
                except self.glue_client.exceptions.EntityNotFoundException:
                    # Definition doesn't exist, register new version
                    response = self.glue_client.register_schema_version(
                        SchemaId={'RegistryName': self.registry_name, 'SchemaName': self.schema_name},
                        SchemaDefinition=self.schema_str
                    )
                    return response['SchemaVersionId']
        except Exception as e:
            print(f"Error registering schema: {e}")
            raise
        
    def serialize(self, data):
        # Convert data to match Avro schema
        avro_data = {
            'userid': int(data['userid']),
            'globalseq': int(data['globalseq']),
            'event_type': str(data['event_type']),
            'product_type': str(data['product_type']),
            'eventtimestamp': int(data['eventtimestamp']),
            'prevglobalseq': int(data['prevglobalseq'])
        }
        
        # Serialize with Avro
        writer = avro.io.DatumWriter(self.avro_schema)
        bytes_writer = io.BytesIO()
        encoder = avro.io.BinaryEncoder(bytes_writer)
        writer.write(avro_data, encoder)
        avro_bytes = bytes_writer.getvalue()
        
        # Prepend GSR header: 2 control bytes + 16 UUID bytes
        control_byte = b'\x03'  # Version 3
        compression_byte = b'\x00'  # No compression
        version_uuid = UUID(self.schema_version_id)
        
        return control_byte + compression_byte + version_uuid.bytes + avro_bytes


def produce_normal_events():
    """Produce normal traffic events continuously"""
    # Log environment variables
    print(f"BOOTSTRAP_SERVER: {os.environ.get('BOOTSTRAP_SERVER', 'NOT_SET')}")
    print(f"TOPIC_NAME: {os.environ.get('TOPIC_NAME', 'NOT_SET')}")
    print(f"AWS_REGION: {os.environ.get('AWS_REGION', 'NOT_SET')}")
    
    tp = MSKTokenProvider()
    
    # Initialize Avro serializer
    registry_name = os.environ.get('SCHEMA_REGISTRY_NAME', 'clickstream-registry')
    schema_name = os.environ.get('SCHEMA_NAME', 'clickstream-event-schema')
    avro_serializer = AvroSerializer(registry_name, schema_name)
    
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
        value_serializer=avro_serializer.serialize,
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
            print(f"Sent Avro event: {data['event_type']} for user {data['userid']}")


if __name__ == "__main__":
    produce_normal_events()
