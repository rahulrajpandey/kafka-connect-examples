#!/bin/bash

echo "Container status:"
docker compose ps

BROKER="kafka-broker:19092"   # INTERNAL listener

echo ""
echo "Topics:"
docker exec -it kafka-broker kafka-topics --bootstrap-server $BROKER --list

echo ""
echo "Broker Cluster ID:"
docker exec -it kafka-broker kafka-metadata-quorum --bootstrap-server $BROKER describe --status