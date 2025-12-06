# Docker Environment – Single-Node KRaft Kafka for Local Development

This directory contains the **Docker Compose environment** used by the Kafka Connect Examples project.  
It provides a **minimal, reliable, single-node KRaft Kafka cluster** suitable for running Java producers/consumers and all upcoming Kafka Connect lessons.

The setup includes:

- Kafka (broker + controller in KRaft mode)
- Multi-listener configuration for both Docker-internal tools and host-based Java applications
- Helper scripts for managing the environment

---

## 1. Directory Structure

docker/
│
├── docker-compose.yml      # Kafka KRaft setup
└── scripts/                # Helper management scripts
├── start.sh
├── stop.sh
├── restart.sh
├── clean.sh
├── create-topics.sh
├── list-topics.sh
├── destroy-topics.sh
├── logs.sh
└── status.sh

Make scripts executable:

```
chmod +x scripts/*.sh
```

⸻

## 2. Starting the Kafka Environment

Start all containers:
```
./scripts/start.sh
```
Or manually:
```
docker compose up -d
```

Check logs:
```
docker logs -f kafka
```

⸻

## 3. Stopping the Environment

Stop containers (preserve data):
```
./scripts/stop.sh
```

Or manually:
```
docker compose down
```

⸻

## 4. Resetting the Environment (Delete All Kafka Data)
```
./scripts/clean.sh
```

Equivalent to:
```
docker compose down -v
```

Use this when metadata or topics need a full reset.

⸻

## 5. Creating Topics

Use the helper script:
```
./scripts/create-topics.sh
```
Default creates:
	•	demo-topic (1 partition, RF=1)

Create manually:
```
docker exec -it kafka kafka-topics \
  --bootstrap-server kafka:19092 \
  --create --topic demo-topic \
  --partitions 1 --replication-factor 1
```

⸻

## 6. Listing Topics
```
./scripts/list-topics.sh
```

Or manually:
```
docker exec -it kafka kafka-topics \
  --bootstrap-server kafka:19092 --list
```

⸻

## 7. Viewing Logs

Tail logs for any container:
```
./scripts/logs.sh kafka
```

⸻

## 8. Environment Status
```
./scripts/status.sh
```

Displays running containers, topics, and KRaft metadata quorum details.

⸻

## 9. Listener Architecture (Important)

This KRaft environment uses two listeners:
```
Listener	Host / Address	Purpose
INTERNAL	kafka:19092	Inter-broker communication & CLI inside Docker
EXTERNAL	localhost:9092	Java producer/consumer running on host
CONTROLLER	kafka:9093	KRaft controller communication
```

Your Java applications must connect using:

bootstrap.servers=localhost:9092

Kafka CLI inside the container uses:

bootstrap.servers=kafka:19092

This multi-listener configuration ensures stable behavior for both Docker-based and host-based clients.

⸻

## 10. Extending This Environment

Future lessons will enhance this Compose setup with:
	•	Kafka Connect Workers
	•	Schema Registry
	•	PostgreSQL / MySQL
	•	Debezium connectors (CDC)
	•	Elasticsearch / OpenSearch
	•	Monitoring stack (Prometheus + Grafana)

Each addition will be documented here as part of subsequent lessons.

⸻

## 11. Troubleshooting

No logs when producer/consumer runs?

Your Java client may not be connecting.
Check:
	•	Using localhost:9092 only
	•	Topic exists
	•	Listener settings in docker-compose.yml
	•	Rebuild with clean.sh if metadata is corrupted

Topic not created?

Ensure you created the topic using internal listener (kafka:19092).

