#!/bin/bash
set -e

echo "WARNING: This will delete ALL Kafka data (topics, offsets)."
read -p "Are you sure? (y/n) " confirm

if [ "$confirm" != "y" ]; then
  echo "Cancelled."
  exit 1
fi

docker compose down -v

echo "Clean reset complete."