#!/bin/bash
set -e

BROKER="kafka-broker:19092"   # INTERNAL listener

echo "Creating topic: demo-topic"

docker exec -it kafka-broker kafka-topics \
  --bootstrap-server $BROKER \
  --create --topic demo-topic \
  --partitions 1 --replication-factor 1 || true

echo "Topic created."