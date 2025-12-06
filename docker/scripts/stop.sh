#!/bin/bash
set -e

echo "Stopping Kafka stack..."
docker compose down

echo "Stopped."