#!/bin/bash
set -e

echo "Starting Kafka stack..."
docker compose up -d

echo "Kafka stack started."
docker compose ps