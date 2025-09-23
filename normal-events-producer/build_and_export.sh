#!/bin/bash

docker build -t normal-flow-log-producer .
docker save -o patient-data-producer.tar normal-flow-log-producer
