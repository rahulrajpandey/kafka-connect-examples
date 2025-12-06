#!/bin/bash

echo "Container status:"
docker compose ps

echo ""
echo "Topics:"
docker exec -it kafka kafka-topics --bootstrap-server kafka:19092 --list

echo ""
echo "Broker Cluster ID:"
docker exec -it kafka kafka-metadata-quorum --bootstrap-server kafka:19092 describe --status