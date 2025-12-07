#!/bin/bash

if [ -z "$1" ]; then
  echo "Usage: ./destroy-topics.sh <topic-name>"
  exit 1
fi

BROKER="kafka-broker:19092"   # INTERNAL listener

docker exec -it kafka-broker kafka-topics \
  --bootstrap-server $BROKER \
  --delete --topic "$1"