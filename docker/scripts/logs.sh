#!/bin/bash

service=${1:-kafka}

echo "Tailing logs for: $service"
docker logs -f $service