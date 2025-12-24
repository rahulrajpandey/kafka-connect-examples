# Debezium CDC → ElasticSearch Sink 

Plan for this exercise
```
MySQL
  |
  |  (binlog)
  v
Debezium MySQL Source (Kafka Connect)
  |
  v
Kafka Topic (CDC events)
  |
  v
ElasticSearch Sink Connector
  |
  v
ElasticSearch Index (searchable CDC stream)
```

ElasticSearch is NOT a replica database. It is a searchable, queryable event projection.

What this example will demonstrate
 - Log-based CDC into ElasticSearch
 - Schema Registry + Avro 
 - Delete handling in search systems
 - Index-per-table strategy
 - How CDC maps naturally to document stores


## Setup
**Step 1: Add ElasticSearch to docker-compose**

<details><summary><strong>Add ElasticSearch and Kibana services to docker-compose.yml</strong></summary>

```yaml
  elasticsearch:
    image: docker.elastic.co/elasticsearch/elasticsearch:8.12.0
    container_name: elasticsearch
    environment:
      - discovery.type=single-node
      - xpack.security.enabled=false
      - ES_JAVA_OPTS=-Xms512m -Xmx512m
    ports:
      - "9200:9200"
    volumes:
      - es-data:/usr/share/elasticsearch/data
    networks:
      - kafka-network
    healthcheck:
      test: [ "CMD-SHELL", "curl -s http://localhost:9200 >/dev/null || exit 1" ]
      interval: 10s
      timeout: 10s
      retries: 10

  kibana:
    image: docker.elastic.co/kibana/kibana:8.12.0
    environment:
      - ELASTICSEARCH_HOSTS=http://elasticsearch:9200
    ports:
      - "5601:5601"
    depends_on:
      - elasticsearch
    networks:
      - kafka-network
```
</details>

**Validations**
1. Start Services
```
docker-compose up -d ElasticSearch ElasticSearch-dashboards
```

2. Validate ElasticSearch is running
```
curl http://localhost:9200

curl http://localhost:9200/_cluster/health?pretty
```

Expected: 
```
{
  "cluster_name" : "docker-cluster",
  "version" : {
    "number" : "8.12.0",
    "build_type" : "docker",
    "build_snapshot" : false,
    "lucene_version" : "9.9.1"
  },
  "tagline" : "You Know, for Search"
}
```

3. Validate ElasticSearch index create, get and delete
```
curl -X PUT http://localhost:9200/test-index \
  -H "Content-Type: application/json" \
  -d '{
    "mappings": {
      "properties": {
        "id":   { "type": "integer" },
        "name": { "type": "keyword" }
      }
    }
  }'


curl http://localhost:9200/test-index/_search?pretty

curl -X DELETE http://localhost:9200/test-index
```

![ElasticSearch-index-validation](ElasticSearch-index-validation.png)

4. Validate Dashboard UI, open in browser:
```
http://localhost:5601
```

**Step 2: Install ElasticSearch Sink Connector**

Update Kafka Connect Dockerfile
```
# ------------------------------------------------------------
# Install ElasticSearch Sink Connector
# ------------------------------------------------------------
RUN confluent-hub install --no-prompt confluentinc/kafka-connect-elasticsearch:15.1.0
```

Rebuild Kafka Connect
```
docker-compose stop kafka-connect
docker-compose build --no-cache kafka-connect
docker-compose up -d kafka-connect
```

**Validations**
1. Verify plugin availability
```
curl http://localhost:8083/connector-plugins | jq '.[] | select(.class | contains("elasticsearch"))'
```

Expected:
```
{
  "class": "org.ElasticSearch.kafka.connect.ElasticSearchSinkConnector",
  "type": "sink",
  "version": "2.12.0"
}
```
![ElasticSearch-Connector-Plugin](ElasticSearch-Connector-Plugin.png)

**Step 3: Permit Debezium Users Replication level privileges**
```
# for administrative setup, login using root user
docker exec -it mysql mysql -uroot -proot

CREATE USER 'debezium'@'%' IDENTIFIED BY 'dbz';

GRANT SELECT, RELOAD, SHOW DATABASES, REPLICATION SLAVE, REPLICATION CLIENT ON *.* TO 'debezium'@'%';

FLUSH PRIVILEGES;
```

**Step 4: Create MySQL Source Table**
```
docker exec -it mysql mysql -u demo -pdemo demo

DROP TABLE IF EXISTS users_os;

CREATE TABLE users_os (
  id INT PRIMARY KEY,
  name VARCHAR(255),
  age INT,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

# these rows will be picked up during snapshot
INSERT INTO users_os (id, name, age) VALUES
(1, 'Rahul', 30),
(2, 'Amit', 28);
```

**Step 5: Create heartbeat and Source Topics**
```
docker exec -it kafka-broker kafka-topics \
  --bootstrap-server kafka-broker:19092 \
  --create \
  --topic dbserver1 \
  --partitions 1 \
  --replication-factor 1

docker exec -it kafka-broker kafka-topics \
  --bootstrap-server kafka-broker:19092 \
  --create \
  --topic dbserver1.demo.users_os \
  --partitions 1 \
  --replication-factor 1
```

---

## Examples
### Ex 1: CDC Event Streaming

**STEP 1: Create ElasticSearch Sink Connector**

<details><summary><strong>Sink Connector Config</strong></summary>

```curl
curl -X POST http://localhost:8083/connectors \
  -H "Content-Type: application/json" \
  -d '{
  "name": "elasticsearch-users-cdc-sink",
  "config": {
    "connector.class": "io.confluent.connect.elasticsearch.ElasticsearchSinkConnector",
    "tasks.max": "1",

    "topics": "dbserver1.demo.users_os",

    "connection.url": "http://elasticsearch:9200",

    "type.name": "_doc",

    "schema.ignore": "false",
    "key.ignore": "true",

    "behavior.on.null.values": "ignore",

    "write.method": "insert",

    "max.in.flight.requests": "1",

    "key.converter": "io.confluent.connect.avro.AvroConverter",
    "key.converter.schema.registry.url": "http://schema-registry:8081",

    "value.converter": "io.confluent.connect.avro.AvroConverter",
    "value.converter.schema.registry.url": "http://schema-registry:8081",

    "transforms": "unwrap",

    "transforms.unwrap.type": "io.debezium.transforms.ExtractNewRecordState",
    "transforms.unwrap.drop.tombstones": "true",
    "transforms.unwrap.delete.handling.mode": "rewrite"
  }
}'
```
</details>

**Response**
```json
{"name":"elasticsearch-users-cdc-sink","config":{"connector.class":"io.confluent.connect.elasticsearch.ElasticsearchSinkConnector","tasks.max":"1","topics":"dbserver1.demo.users_os","connection.url":"http://elasticsearch:9200","type.name":"_doc","schema.ignore":"false","key.ignore":"true","behavior.on.null.values":"ignore","write.method":"insert","max.in.flight.requests":"1","key.converter":"io.confluent.connect.avro.AvroConverter","key.converter.schema.registry.url":"http://schema-registry:8081","value.converter":"io.confluent.connect.avro.AvroConverter","value.converter.schema.registry.url":"http://schema-registry:8081","transforms":"unwrap","transforms.unwrap.type":"io.debezium.transforms.ExtractNewRecordState","transforms.unwrap.drop.tombstones":"true","transforms.unwrap.delete.handling.mode":"rewrite","name":"elasticsearch-users-cdc-sink"},"tasks":[],"type":"sink"}
```

**Verify Sink Connector is running**
```
curl http://localhost:8083/connectors/elasticsearch-users-cdc-sink/status | jq
```

**STEP 2: Create Debezium MySQL Source Connector**
<details><summary><strong>Source Connector</strong></summary>

```curl
curl -X POST http://localhost:8083/connectors \
  -H "Content-Type: application/json" \
  -d '{
    "name": "debezium-mysql-users_os",
    "config": {
      "connector.class": "io.debezium.connector.mysql.MySqlConnector",

      "tasks.max": "1",

      "database.hostname": "mysql",
      "database.port": "3306",
      "database.user": "debezium",
      "database.password": "dbz",

      "database.server.id": "184054",
      "topic.prefix": "dbserver1",

      "database.include.list": "demo",
      "table.include.list": "demo.users_os",

      "snapshot.mode": "initial",

      "schema.history.internal.kafka.bootstrap.servers": "kafka-broker:19092",
      "schema.history.internal.kafka.topic": "schema-history.demo",

      "key.converter": "io.confluent.connect.avro.AvroConverter",
      "key.converter.schema.registry.url": "http://schema-registry:8081",

      "value.converter": "io.confluent.connect.avro.AvroConverter",
      "value.converter.schema.registry.url": "http://schema-registry:8081"
    }
  }'
```
</details>

OUTPUT:
```json
{"name":"debezium-mysql-users_os","config":{"connector.class":"io.debezium.connector.mysql.MySqlConnector","tasks.max":"1","database.hostname":"mysql","database.port":"3306","database.user":"debezium","database.password":"dbz","database.server.id":"184054","topic.prefix":"dbserver1","database.include.list":"demo","table.include.list":"demo.users_os","snapshot.mode":"initial","schema.history.internal.kafka.bootstrap.servers":"kafka-broker:19092","schema.history.internal.kafka.topic":"schema-history.demo","key.converter":"io.confluent.connect.avro.AvroConverter","key.converter.schema.registry.url":"http://schema-registry:8081","value.converter":"io.confluent.connect.avro.AvroConverter","value.converter.schema.registry.url":"http://schema-registry:8081","name":"debezium-mysql-users_os"},"tasks":[],"type":"source"}
```


**Verify Source Connector is running**
```
curl http://localhost:8083/connectors/debezium-mysql-users_os/status | jq
```

**Step 3: Validations**

**Verify snapshot execution**
```
docker logs kafka-connect | grep -i snapshot
```

Verify Subject Registration
```
curl http://localhost:8081/subjects
```
![subject-registration](subject-registration.png)

**Verify ElasticSearch Index Creation**
```
curl http://localhost:9200/_cat/indices?v
```
Expected: `dbserver1.demo.users_os`


Query Data in ElasticSearch
```
curl http://localhost:9200/dbserver1.demo.users_os/_search?pretty
```
![snapshot-data-in-elasticsearch](snapshot-data-in-elasticsearch.png)


View Snapshot Data in Kibana UI
![kibana-snapshot-ui](kibana-snapshot-ui.png)

**STEP 4: Test Live CDC Flow**

```
docker exec -it mysql mysql -u demo -pdemo demo

INSERT INTO users_os (id, name, age) VALUES (99, 'OS-Test', 40);
UPDATE users_os SET age=41 WHERE id=99;

```
![insert-update-demo](insert-update-demo.png)
![kibana-live-update-data-ui](kibana-live-update-data-ui.png)


```
DELETE FROM users_os WHERE id=99;
```
![delete-demo](delete-demo.png)
![kibana-live-delete-data-ui](kibana-live-delete-data-ui.png)


---

### Ex 2: Schema Evolution (Add Column)

Db Change:
```
ALTER TABLE users_os ADD COLUMN email VARCHAR(255);

INSERT INTO users_os (id, name, age, email) VALUES (100, 'Schema-Test', 35, 'schema@test.com');
```

The change in Schema is not being picked by the ElasticSearch Sink Connector as we have used `"schema.ignore": "false" `
which essentially ignores any new update to the schema after this connector has been registered.
So let's delete the sink connector and re-register with schema.ignore to true.
Then connector:
 - Stops enforcing Kafka Connect schemas
 - Sends plain JSON documents to Elasticsearch
 - Lets Elasticsearch dynamically infer fields
 - Allows schema evolution to appear automatically

```
curl -X DELETE http://localhost:8083/connectors/elasticsearch-users-cdc-sink
```

**Create ElasticSearch Sink Connector**

<details><summary><strong>Sink Connector Config</strong></summary>

```curl
curl -X POST http://localhost:8083/connectors \
  -H "Content-Type: application/json" \
  -d '{
  "name": "elasticsearch-users-cdc-sink",
  "config": {
    "connector.class": "io.confluent.connect.elasticsearch.ElasticsearchSinkConnector",
    "tasks.max": "1",

    "topics": "dbserver1.demo.users_os",

    "connection.url": "http://elasticsearch:9200",

    "type.name": "_doc",

    "schema.ignore": "true",
    "key.ignore": "true",

    "behavior.on.null.values": "ignore",

    "write.method": "insert",

    "max.in.flight.requests": "1",

    "key.converter": "io.confluent.connect.avro.AvroConverter",
    "key.converter.schema.registry.url": "http://schema-registry:8081",

    "value.converter": "io.confluent.connect.avro.AvroConverter",
    "value.converter.schema.registry.url": "http://schema-registry:8081",

    "transforms": "unwrap",

    "transforms.unwrap.type": "io.debezium.transforms.ExtractNewRecordState",
    "transforms.unwrap.drop.tombstones": "true",
    "transforms.unwrap.delete.handling.mode": "rewrite"
  }
}'
```
</details>

**Validations**

i) As we added new field email in the table, there should be a new schema registered for this subject.
```
curl http://localhost:8081/subjects | jq

curl http://localhost:8081/subjects/dbserver1.demo.users_os-value/versions | jq

curl http://localhost:8081/subjects/dbserver1.demo.users_os-value/versions/latest | jq
```
![schema-evolution-ex1-registry](schema-evolution-ex1-registry.png)

ii) Verify key schema stability
```
curl http://localhost:8081/subjects/dbserver1.demo.users_os-key/versions | jq
```
- Primary key (id) did not change
- Keys should be stable
- This ensures partitioning and compaction correctness

![schema-evolution-ex1-key-stable](schema-evolution-ex1-key-stable.png)

iii) Kafka Topic Data

![schema-evolution-ex1-consumption](schema-evolution-ex1-consumption.png)

iv) Index in ElasticSearch must update
```
curl http://localhost:9200/dbserver1.demo.users_os/_search\?pretty

curl http://localhost:9200/dbserver1.demo.users_os/_mapping | jq
```
![schema-evolution-ex1-mapping](schema-evolution-ex1-mapping.png)

![schema-evolution-ex1-search](schema-evolution-ex1-search.png)

### Ex 2: Schema Evolution (Drop Column)
Capture:
- Schema Registry changes
- ES index mappings
- Failure scenarios




### Ex 2: Schema Evolution (Modify Column Type)
Capture:
- Schema Registry changes
- ES index mappings
- Failure scenarios





---
Cleanup

```
curl -X DELETE http://localhost:8083/connectors/elasticsearch-users-cdc-sink

curl -X DELETE http://localhost:8083/connectors/debezium-mysql-users_os

```


