#!/bin/bash

service=${1:-kafka-broker}

echo "Tailing logs for: $service"
docker logs -f $service