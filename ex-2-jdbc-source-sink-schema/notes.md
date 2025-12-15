Setup Needed: 
1. SchemaRegistry Setup

Schema Registry needs only:
- 	Kafka bootstrap servers
-	A group ID
-	A topic to store schemas (created automatically)
-	REST port

Add SchemaRegistry service in docker-compose.

Bring the Infra Up:
```
docker-compose up -d

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

---
2. MySQL Setup 

Add mysql service in docker-compose.yaml file.

Start MySQL: 
`docker-compose up -d mysql`

Verify: 
`docker exec -it mysql mysql -u demo -pdemo demo`

This should start a prompt of MySQL like this, hit `Ctrl + z` to exit.
![mysql-setup](mysql-setup.png)

**Create sample table**
```
CREATE TABLE users (
  id INT PRIMARY KEY,
  name VARCHAR(255),
  age INT,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO users (id, name, age) VALUES
(1, 'Rahul', 30),
(2, 'Amit', 28);
```
![mysql-table-created](mysql-table-created.png)

3. JDBC Driver in custom-kafka-connect 

Update Dockerfile to download `kafka-connect-jdbc` and `mysql-connector` jars.

Rebuild kafka-connect: 
```
docker-compose stop kafka-connect
docker-compose build --no-cache kafka-connect
docker-compose up -d kafka-connect
```

Verify the jars are downloaded and properly placed inside pod:
![jdbc-connectors-downloaded](jdbc-connectors-downloaded.png)

Verify Connector Plugins are loaded: 
```
curl http://localhost:8083/connector-plugins | jq
```
![jdbc-connectors](jdbc-connectors-jars.png)

---
## Example 1 - JDBC Source/Sink (MySQL) + Schema Registry
What this example will demonstrate
-	Structured source (Struct + Schema)
-	Schema Registry actually registering subjects
-	Strict type enforcement
-	End-to-end: MySQL → Kafka → MySQL/File
-	Formats we will cover:
1.	JSON Schema
2.	Avro
3.	Protobuf

**canonical Kafka Connect + Schema Registry pipeline**
```
MySQL table
    ↓ (JDBC Source)
Kafka Connect (Struct + Schema)
    ↓ (Converter)
Kafka topic (schema-backed)
    ↓ (Sink)
MySQL / File
```


### i) Configure Kafka Connect for Schema Registry (JSON Schema)
Update Kafka Connect Service:

```
CONNECT_KEY_CONVERTER: "org.apache.kafka.connect.storage.StringConverter"

CONNECT_VALUE_CONVERTER: "io.confluent.connect.json.JsonSchemaConverter"
CONNECT_VALUE_CONVERTER_SCHEMA_REGISTRY_URL: "http://schema-registry:8081"

CONNECT_VALUE_CONVERTER_AUTO_REGISTER_SCHEMAS: "true"
CONNECT_VALUE_CONVERTER_USE_LATEST_VERSION: "true"
```

### ii) Restart Kafka Connect
```
docker-compose up -d --force-recreate kafka-connect
```

validate: 
```
curl http://localhost:8083/
```

### iii) FileStream Source Connector (JSON Schema)

**JDBC Source Connector Config and registration**
```
curl -X POST http://localhost:8083/connectors \
  -H "Content-Type: application/json" \
  -d '{
    "name": "mysql-users-source",
    "config": {
      "connector.class": "io.confluent.connect.jdbc.JdbcSourceConnector",
      "tasks.max": "1",

      "connection.url": "jdbc:mysql://mysql:3306/demo",
      "connection.user": "demo",
      "connection.password": "demo",

      "table.whitelist": "users",
      "mode": "incrementing",
      "incrementing.column.name": "id",

      "topic.prefix": "mysql-",
      
      "topic.creation.enable": "true",
      "topic.creation.default.partitions": "1",
      "topic.creation.default.replication.factor": "1"
    }
  }'
```
![verify-source-connector](JDBC-SOURCE-CONNECTOR.png)

What it will do under the hood is that: 
- Register this Source Connector
- Register the schema
- Create topic

**Verify Connector registration**
```
curl http://localhost:8083/connectors
```
Expected:
["mysql-users-source"]

Check status of Connector: 
```
curl http://localhost:8083/connectors/mysql-users-source/status
```
Expected: `"state": "RUNNING"`

### v) Verify Schema Registration
```
curl http://localhost:8081/subjects
```
Expected:
["mysql-users-value"]

![verify-schema-registration](verify-schema-registration.png)

Check Schema: 
```
curl http://localhost:8081/subjects/mysql-users-value/versions/latest
```
![schema-check](schema-check.png)

**Test and Demo:** 

Read from topic: 
```
docker exec -it kafka-broker kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic mysql-users \
  --from-beginning
```

Insert into table:

```
INSERT INTO users (id, name, age) VALUES
(3, 'Raushan', 31),
(4, 'Amrit', 26);
```

![JDBC-Source-Connector-Demo](JDBC-SOURCE-CONNECTOR-DEMO.png)







---

Cleanup: 
```
curl -X DELETE http://localhost:8083/connectors/mysql-users-source

curl -X DELETE http://localhost:8083/connectors/json-schema-sink

Soft Delete Subject: 
curl -X DELETE http://localhost:8081/subjects/mysql-users-value

curl http://localhost:8081/subjects

Hard Delete Subject:

curl -X DELETE "http://localhost:8081/subjects/mysql-users-value?permanent=true"
  
```
## JSON Schema Converter

Important Limitation (JSON + FileStreamSource)

FileStreamSourceConnector emits each line as a String, not a structured JSON object.
As a result:
- Schema Registry cannot enforce JSON field validation
- JSON Schema validation does not work with this connector

Schema Registry requires a structured source (JDBC, Debezium, REST, or custom connector), we will cover this later in upcoming exercises.


---
Just for understanding: 
We did auto-registration of schema while registering the source connector.
So when first valid record is produced, Kafka Connect and JsonSchemaConverter infers the schema and registers it under `users-json-value` and all future records are validated against this schema.

Mental Model to build:
- Kafka stores bytes.
- Schema Registry stores contracts.
- Converters enforce correctness.

Use of FileStreamConnectors is just for learning purpose, for Production usage use something which provides more control as FileStreamConnectors often misses bulk data read or parsing the data properly.