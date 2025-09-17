#!/bin/bash

# Build the Docker image
docker build -t test-data-generator .

# Run tests in container
docker run --rm -v $(pwd):/app -w /app test-data-generator:latest python test_with_env.py
