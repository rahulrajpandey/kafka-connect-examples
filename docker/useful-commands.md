## Connectors Command

**Delete Connector from Kafka-Connect**
```
curl -X DELETE http://localhost:8083/connectors/{connector-name}
```

**List connectors**
```
curl -s localhost:8083/connectors | jq
```

**Fetch Connector config**
```
curl -s localhost:8083/connectors/{connector-name} | jq
```

**Status**
```
curl -s localhost:8083/connectors/{connector-name}/status | jq
```

**Pause / Resume**
```
curl -X PUT localhost:8083/connectors/{connector-name}/pause

curl -X PUT localhost:8083/connectors/{connector-name}/resume
```

**Restart**
```
curl -X POST localhost:8083/connectors/{connector-name}/restart
```

**List only source connectors**
```
curl -s localhost:8083/connectors \
  | jq -r '.[]' \
  | xargs -I {} "curl -s localhost:8083/connectors/{}/" \
  | jq 'select(.type == "source") | .name'
```

**List only sink connectors**
```
curl -s localhost:8083/connectors \
  | jq -r '.[]' \
  | xargs -I {} "curl -s localhost:8083/connectors/{}/" \
  | jq 'select(.type == "sink") | .name'
```

---

## SchemaRegistry Command

**Get Subject from SchemaRegistry**
```
# Fetch Subjects

curl http://localhost:8081/subjects

curl http://localhost:8081/subjects/{subject-name}/versions/latest
```

**Delete Subject from SchemaRegistry**
```
# Soft Delete Subject: 

curl -X DELETE http://localhost:8081/subjects/{subject-name}

# Hard Delete Subject:

curl -X DELETE "http://localhost:8081/subjects/{subject-name}?permanent=true"
```

---

## Kafka Commands

**Create topic**
```
docker exec -it kafka-broker kafka-topics \
  --bootstrap-server kafka-broker:19092 \
  --create \
  --topic {topic-name} \
  --partitions 1 \
  --replication-factor 1
```

**List all topics**
```
docker exec -it kafka-broker \
  kafka-topics --bootstrap-server kafka-broker:19092 --list
```

**Read from topic**
```
docker exec -it kafka-broker kafka-console-consumer \
  --bootstrap-server kafka-broker:19092 \
  --topic {topic-name} \
  --from-beginning
```

**Delete a topic**
```
docker exec -it kafka-broker kafka-topics \
  --bootstrap-server kafka-broker:19092 \
  --delete --topic {topic-name}
```
---


## MySQL Commands

```
# for administrative setup, login using root user
docker exec -it mysql mysql -uroot -proot

# for normal DB Operations
docker exec -it mysql mysql -u demo -pdemo demo

drop table if exists table-name;

select * from table-name;

```


