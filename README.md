# Kafka Connect Examples – Complete Learning Project

This repository is a structured, incremental learning environment for understanding **Apache Kafka**, **Kafka Connect**, and real-world data pipelines.  
The project starts with minimal Kafka fundamentals and expands step-by-step into advanced scenarios including Connect workers, JDBC Source/Sink, Debezium CDC, Elasticsearch sinks, and custom connector development.

This is a hands-on, production-grade learning setup built with:

- **Single-node KRaft Kafka** (for local development)
- **Maven multi-module Java project**
- **Isolated Docker Compose environment**
- **Incremental examples structured by lessons**

---

## 1. Repository Structure
kafka-connect-examples/
│
├── README.md                  # You are here (project overview)
│
├── docker/
│   ├── README.md              # Docker/Kafka runtime environment documentation
│   ├── docker-compose.yml     # Single-node KRaft Kafka environment
│   └── scripts/               # Utility scripts (start/stop/reset/topics)
│
└── java/
├── README.md              # Java examples documentation
├── pom.xml                # Maven parent project
├── utilities/             # Producer/Consumer demos
└── examples/              # Kafka Connect examples (added lesson by lesson)

---

## 2. Current Examples Status

| Example | Content | Status |
|---------|---------|--------|
| Example 1 | Minimal Kafka + Java Producer/Consumer | Completed |
| Example 2 | Kafka Connect – FileStream Source/Sink | Pending |
| Example 3 | Schema Registry + Avro/JSON schema support | Pending |
| Example 4 | JDBC Source/Sink (PostgreSQL/MySQL) | Pending |
| Example 5 | Debezium CDC (MySQL/Postgres) | Pending |
| Example 6 | OpenSearch/Elasticsearch Sink | Pending |
| Example 7 | Custom Kafka Connect Connector | Pending |
| Example 8 | Distributed Connect Cluster | Pending |
| Example 9 | Monitoring Kafka + Connect | Pending |
| Example 10 | End-to-End Real-Time Pipeline | Pending |

You can navigate to each lesson through the `java/examples/` directory as they are added.

---

## 3. Quick Start (Lesson 1)

### Start Kafka:

```
cd docker
./scripts/start.sh
```

### Create topic:
```
./scripts/create-topics.sh
```

### Build Java examples:
```
mvn -f java clean package
```

### Run producer:
```
java -cp java/utilities/producer-demo/target/producer-demo-1.0-SNAPSHOT.jar \
     com.rrp.producer.SimpleProducer
```

### Run Consumer:
```
java -cp java/utilities/consumer-demo/target/consumer-demo-1.0-SNAPSHOT.jar \
     com.rrp.consumer.SimpleConsumer
```


## 4. Future Roadmap

This project will evolve into a full Kafka Connect demonstration environment including:
	•	Real databases (Postgres, MySQL)
	•	Elasticsearch/OpenSearch
	•	Schema evolution scenarios
	•	CDC pipelines using Debezium
	•	Connect cluster failover behaviors
	•	Custom connector build & deployment
	•	Monitoring & observability stack

⸻

## 5. Requirements
	•	Docker + Docker Compose
	•	Java 17+
	•	Maven 3.9+

⸻

## 6. Contributing

Pull requests or suggestions to enhance examples, lessons, or documentation are welcome.