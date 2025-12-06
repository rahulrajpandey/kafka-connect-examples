#!/bin/bash

if [ -z "$1" ]; then
  echo "Usage: ./destroy-topics.sh <topic-name>"
  exit 1
fi

docker exec -it kafka kafka-topics \
  --bootstrap-server kafka:19092 \
  --delete --topic "$1"