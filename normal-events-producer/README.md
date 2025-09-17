# Normal Events Producer

## Setup Virtual Environment

### Override externally managed restriction:
```bash
python3 -m venv venv
source venv/bin/activate
pip install -r requirements.txt --break-system-packages
```

## Running Tests

### Test producer functions:
```bash
python test_functions.py
```

### Test with mocked Kafka:
```bash
python test_with_env.py
```

## Running Producer

### Set environment variables:
```bash
export BOOTSTRAP_SERVER="your-kafka-server:9092"
export TOPIC_NAME="your-topic-name"
```

### Run producer:
```bash
python normal_events_producer.py
```

## Customizing Data Generator

### Update for your use case:
1. Edit `data_generator.py` to implement your specific data generation logic
2. Follow the AI prompts in the class docstrings for guidance
3. Ensure the `main(event)` function returns: `{"statusCode": 200, "events": [...], "events_generated": count}`

### Test your implementation:
```bash
# Test data generation locally
python data_generator.py

# Test with Docker
docker build -t test-data-generator .
docker run test-data-generator python data_generator.py
```

## Docker

### Build container:
```bash
docker build -t test-data-generator .
```

### Test dockerized data generator:
```bash
docker run test-data-generator python data_generator.py
```

### Run producer container:
```bash
docker run -i -e BOOTSTRAP_SERVER="your-kafka-server:9092" -e TOPIC_NAME="your-topic" test-data-generator:latest 
```

### Export image to assets:
```bash
# Create assets directory if it doesn't exist
mkdir -p ../assets

# Save Docker image to tar file
docker save test-data-generator:latest > ../assets/test-data-generator.tar

# Verify export
ls -la ../assets/test-data-generator.tar
```

### Upload to workshop assets:
```bash
# Upload to your workshop assets bucket
aws s3 cp ../assets/test-data-generator.tar s3://your-workshop-assets-bucket/

# Or use your preferred upload method
```

## Development Workflow

1. **Customize**: Update `data_generator.py` with your business logic
2. **Test locally**: Run `python data_generator.py` to verify output
3. **Test dockerized**: Build and test with Docker commands above
4. **Export**: Save image to `../assets/` directory
5. **Deploy**: Upload to workshop assets for distribution

## Deactivate Virtual Environment
```bash
deactivate
```