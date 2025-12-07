#!/bin/bash
BROKER="kafka-broker:19092"   # INTERNAL listener
docker exec -it kafka-broker kafka-topics --bootstrap-server $BROKER --list