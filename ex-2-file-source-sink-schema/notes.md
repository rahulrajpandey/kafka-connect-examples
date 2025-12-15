1. What Schema Registry Needs 

Schema Registry needs only:
- 	Kafka bootstrap servers
-	A group ID
-	A topic to store schemas (created automatically)
-	REST port

Add SchemaRegistry service in docker-compose.

Bring the Infra Up:
```
docker-compose up -d
```
```
docker ps
```

Validate Schema Registry
Check health
```
curl http://localhost:8081/subjects
```

Verify _schemas topic exists
```
docker exec -it kafka-broker \
  kafka-topics --bootstrap-server kafka-broker:19092 --list
```
_schemas should be there in the list of topics.