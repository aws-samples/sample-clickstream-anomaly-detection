#!/bin/bash
mkdir -p assets
zip -r ./assets/event-anomaly-detection-code.zip \
  app.py \
  requirements.txt \
  cdk.json \
  code/