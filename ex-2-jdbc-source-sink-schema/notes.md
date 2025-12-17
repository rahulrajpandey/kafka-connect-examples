# Kafka Connect + Schema Registry — JDBC Examples (MySQL)

This document walks step by step through using **Kafka Connect with Schema Registry**
using **JDBC Source and Sink connectors** with MySQL.

---

## Setup Needed

### 1. Schema Registry Setup

Schema Registry needs only:
- Kafka bootstrap servers
- A group ID
- A topic to store schemas (`_schemas`, created automatically)
- A REST port

Add Schema Registry service in `docker-compose.yaml`.

Bring the infra up:

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

### 2. MySQL Setup

Add a MySQL service in docker-compose.yaml.

Start MySQL:
`docker-compose up -d mysql`

Verify connectivity:
`docker exec -it mysql mysql -u demo -pdemo demo`

This should start a prompt of MySQL like this, hit `Ctrl + z` to exit.
![mysql-setup](mysql-setup.png)

**Create a sample table**
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

### 3. JDBC Driver in custom Kafka Connect Image

Kafka Connect does not ship with database drivers by default.

Update the Dockerfile to install:
- kafka-connect-jdbc
- mysql-connector

Rebuild Kafka Connect:
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
## Example - JDBC Source/Sink (MySQL) + Schema Registry
What this example demonstrates
-	Structured data (Struct + Schema)
-	Schema Registry subject registration
-	Strict type enforcement
-	End-to-end: MySQL → Kafka → MySQL/File
-	Formats we will cover:
1.	JSON 
2.	Avro


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


### JSON Schema Example
### 1. Configure Kafka Connect for Schema Registry (JSON Schema)
**Update Kafka Connect Service environment:**

```
CONNECT_KEY_CONVERTER: "org.apache.kafka.connect.storage.StringConverter"

CONNECT_VALUE_CONVERTER: "io.confluent.connect.json.JsonSchemaConverter"
CONNECT_VALUE_CONVERTER_SCHEMA_REGISTRY_URL: "http://schema-registry:8081"

CONNECT_VALUE_CONVERTER_AUTO_REGISTER_SCHEMAS: "true"
CONNECT_VALUE_CONVERTER_USE_LATEST_VERSION: "true"
```

**Restart Kafka Connect**
```
docker-compose up -d --force-recreate kafka-connect
```

**Verify** 
```
curl http://localhost:8083/
```

### 2. JDBC Source Connector (JSON Schema)

We use incrementing mode here.

Important behavior of mode=incrementing:
- Kafka Connect tracks the largest value of the incrementing column.
- On startup, it may initialize its offset to the current MAX(id).
- This effectively means: “start from now”, not “read everything”.

Example:
- Table already has id = 1, 2
- Connector starts
- Offset initialized as last_seen_id = 2
- Query becomes: SELECT * FROM users WHERE id > 2

So rows 1 and 2 are not emitted.

This mode is suitable for append-only streams, not historical backfill.

**Register JDBC Source Connector**
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

---

**Important Concept: Lazy Creation**

Kafka Connect creates:
- Topics
- Schema Registry subjects

only when records are produced.

Registering a connector alone does nothing to Kafka or Schema Registry.

That is why:
- No new rows → no topic → no schema

In production, teams do not rely on JDBC Source for tables that already contain data.
They use CDC tools like Debezium, often with a one-time bulk load.

---

**Produce Data**
```
docker exec -it mysql mysql -u demo -pdemo demo

INSERT INTO users (id, name, age) VALUES
(3, 'Raushan', 31),
(4, 'Amrit', 26);
```

**Verify Topic**
```
docker exec -it kafka-broker kafka-console-consumer \
  --bootstrap-server kafka-broker:19092 \
  --topic mysql-users \
  --from-beginning
```

**Verify Schema Registration**
```
curl http://localhost:8081/subjects
```
Expected:
["mysql-users-value"]

![verify-schema-registration](verify-schema-registration.png)

**Check Schema** 
```
curl http://localhost:8081/subjects/mysql-users-value/versions/latest
```
![schema-check](schema-check.png)

![JDBC-Source-Connector-Demo](JDBC-SOURCE-CONNECTOR-DEMO.png)


### Sink Connectors (JSON Schema)

```
MySQL (users table)
        |
        |  JDBC Source Connector
        v
Kafka topic: mysql-users
        |
        +---------------------------+
        |                           |
File Sink Connector        JDBC Sink Connector
(mysql-users.out)               (users_processed table)
        |
   SMTs applied              SMTs applied
```

### 3. File Sink Connector (with Transformation)
Write Kafka records to a file after masking / renaming fields. In this case we are masking `name` field, so in sink file the name field will have empty value.

File Sink config:
```
curl -X POST http://localhost:8083/connectors \
  -H "Content-Type: application/json" \
  -d '{
    "name": "mysql-users-file-sink",
    "config": {
      "connector.class": "org.apache.kafka.connect.file.FileStreamSinkConnector",
      "tasks.max": "1",

      "topics": "mysql-users",
      "file": "/tmp/sink/mysql-users.out",

      "key.converter": "org.apache.kafka.connect.storage.StringConverter",

      "value.converter": "io.confluent.connect.json.JsonSchemaConverter",
      "value.converter.schema.registry.url": "http://schema-registry:8081",

      "transforms": "MaskName,RenameField",

      "transforms.MaskName.type": "org.apache.kafka.connect.transforms.MaskField$Value",
      "transforms.MaskName.fields": "name",

      "transforms.RenameField.type": "org.apache.kafka.connect.transforms.ReplaceField$Value",
      "transforms.RenameField.renames": "age:user_age"
    }
  }'
```

**Verify**
```
curl http://localhost:8083/connectors

curl http://localhost:8083/connectors/mysql-users-file-sink/status
```

### 4. JDBC Sink Connector (Kafka → MySQL) (with transformation)
Write transformed Kafka records into another MySQL table.
As part of transformation, we will remove created_at field from source table and then write the rows into sink table.

**Create Target Table**
```
docker exec -it mysql mysql -u demo -pdemo demo

CREATE TABLE users_processed (
  id INT PRIMARY KEY,
  user_name VARCHAR(100),
  user_age INT
);
```

**JDBC Sink config**
```
curl -X POST http://localhost:8083/connectors \
  -H "Content-Type: application/json" \
  -d '{
    "name": "mysql-users-jdbc-sink",
    "config": {
      "connector.class": "io.confluent.connect.jdbc.JdbcSinkConnector",
      "tasks.max": "1",

      "topics": "mysql-users",

      "connection.url": "jdbc:mysql://mysql:3306/demo",
      "connection.user": "demo",
      "connection.password": "demo",

      "auto.create": "false",
      "auto.evolve": "false",
      "insert.mode": "upsert",
      "pk.mode": "record_value",
      "pk.fields": "id",

      "table.name.format": "users_processed",
      
      "key.converter": "org.apache.kafka.connect.storage.StringConverter",

      "value.converter": "io.confluent.connect.json.JsonSchemaConverter",
      "value.converter.schema.registry.url": "http://schema-registry:8081",

      "transforms": "Rename,DropField",

      "transforms.Rename.type": "org.apache.kafka.connect.transforms.ReplaceField$Value",
      "transforms.Rename.renames": "name:user_name,age:user_age",

      "transforms.DropField.type": "org.apache.kafka.connect.transforms.ReplaceField$Value",
      "transforms.DropField.blacklist": "created_at"
    }
  }'
```

**Verify**
```
curl http://localhost:8083/connectors

curl http://localhost:8083/connectors/mysql-users-jdbc-sink/status
```

End to End test for Source to Sink using JSON Schema:
![JSON Schema E2E Test](JSON-Schema-E2E-Test.png)


**Cleanup** 
```
curl -X DELETE http://localhost:8083/connectors/mysql-users-source
curl -X DELETE http://localhost:8083/connectors/mysql-users-file-sink
curl -X DELETE http://localhost:8083/connectors/mysql-users-jdbc-sink

# Soft Delete Subject: 
curl -X DELETE http://localhost:8081/subjects/mysql-users-value

# Fetch Subjects
curl http://localhost:8081/subjects

# Hard Delete Subject:
curl -X DELETE "http://localhost:8081/subjects/mysql-users-value?permanent=true"
```

---
### JSON Schema Example
### 1. Configure Kafka Connect for Schema Registry (AVRO Schema)
The Avro example follows the exact same flow, only the converter changes.

Key differences:
- Kafka topic stores binary Avro
- Schema is stricter and compact
- Kafka Connect still works with Struct + Schema internally

Important Observations
- Kafka topic → binary Avro
- File Sink / JDBC Sink → readable output
- Console consumer → unreadable binary unless Avro-aware

**Key Behavior Reminder**

In `mode=incrementing`:
- Kafka Connect tracks last_seen_id = MAX(id)
- Rows with smaller IDs are ignored
- This behavior is independent of Avro or JSON

JDBC Source (Avro)
```
curl -X POST http://localhost:8083/connectors \
  -H "Content-Type: application/json" \
  -d '{
    "name": "mysql-users-source-avro",
    "config": {
      "connector.class": "io.confluent.connect.jdbc.JdbcSourceConnector",
      "tasks.max": "1",

      "connection.url": "jdbc:mysql://mysql:3306/demo",
      "connection.user": "demo",
      "connection.password": "demo",

      "table.whitelist": "users",
      "mode": "incrementing",
      "incrementing.column.name": "id",

      "topic.prefix": "mysql-avro-",

      "topic.creation.enable": "true",
      "topic.creation.default.partitions": "1",
      "topic.creation.default.replication.factor": "1",

      "key.converter": "org.apache.kafka.connect.storage.StringConverter",

      "value.converter": "io.confluent.connect.avro.AvroConverter",
      "value.converter.schema.registry.url": "http://schema-registry:8081"
    }
  }'
```

Check status
```
curl http://localhost:8083/connectors/mysql-users-source-avro/status
```

Expected result
-	Topic: mysql-avro-users
-	Subject: mysql-avro-users-value 

Verify Avro schema in Schema Registry
```
curl http://localhost:8081/subjects
curl http://localhost:8081/subjects/mysql-avro-users-value/versions/latest
```

Expected:
["mysql-avro-users-value"]

Read from topic:
```
docker exec -it kafka-broker kafka-console-consumer \
  --bootstrap-server kafka-broker:19092 \
  --topic mysql-avro-users \
  --from-beginning
```

###  2. File Sink Connector (without Transformation)
```
curl -X POST http://localhost:8083/connectors \
  -H "Content-Type: application/json" \
  -d '{
    "name": "mysql-users-file-sink-avro",
    "config": {
      "connector.class": "org.apache.kafka.connect.file.FileStreamSinkConnector",
      "tasks.max": "1",

      "topics": "mysql-avro-users",
      "file": "/tmp/sink/mysql-users-avro.out",

      "key.converter": "org.apache.kafka.connect.storage.StringConverter",

      "value.converter": "io.confluent.connect.avro.AvroConverter",
      "value.converter.schema.registry.url": "http://schema-registry:8081"
    }
  }'
```

### 3. JDBC Sink with Avro (minimal, no SMTs)

Create a new sink table

```
CREATE TABLE users_processed_avro (
  id INT PRIMARY KEY,
  name VARCHAR(100),
  age INT,
  created_at TIMESTAMP
);
```

JDBC Sink 

```
curl -X POST http://localhost:8083/connectors \
  -H "Content-Type: application/json" \
  -d '{
    "name": "mysql-users-jdbc-sink-avro",
    "config": {
      "connector.class": "io.confluent.connect.jdbc.JdbcSinkConnector",
      "tasks.max": "1",

      "topics": "mysql-avro-users",

      "connection.url": "jdbc:mysql://mysql:3306/demo",
      "connection.user": "demo",
      "connection.password": "demo",

      "auto.create": "false",
      "auto.evolve": "false",

      "insert.mode": "upsert",
      "pk.mode": "record_value",
      "pk.fields": "id",

      "table.name.format": "users_processed_avro",

      "key.converter": "org.apache.kafka.connect.storage.StringConverter",

      "value.converter": "io.confluent.connect.avro.AvroConverter",
      "value.converter.schema.registry.url": "http://schema-registry:8081"
    }
  }'
```

**Verify**
```
curl http://localhost:8083/connectors/mysql-users-jdbc-sink-avro/status
```
End to End Demo: 
![AVRO-Schema-E2E-Demo](AVRO-Schema-E2E-Demo.png)

Note: 
In this demo, we inserted, 4 records:
```
INSERT INTO users (id, name, age) VALUES (501, 'Avro-New', 39);
INSERT INTO users (id, name, age) VALUES (7, 'Avro-New', 49);
INSERT INTO users (id, name, age) VALUES (8, 'Avro-New2', 49);
INSERT INTO users (id, name, age) VALUES (502, 'Avro-New2', 49);
```

Only id=501 and id=502 rows were picked up by connector because we are using 
```
mode=incrementing
incrementing.column.name=id
```
Kafka Connect JDBC Source maintains an offset like this: last_seen_id = MAX(id) that was successfully produced.
So once it processed id=501 it ignored id=7 and id=8 rows and then processed id=502 row.

Things to know:
- Kafka topic → binary Avro
- File Sink / JDBC Sink → readable values
- Console consumer → unreadable binary

Cleanup: 
```
curl -X DELETE http://localhost:8083/connectors/mysql-users-source-avro
curl -X DELETE http://localhost:8083/connectors/mysql-users-file-sink-avro
curl -X DELETE http://localhost:8083/connectors/mysql-users-jdbc-sink-avro

# Soft Delete Subject: 
curl -X DELETE http://localhost:8081/subjects/mysql-users-value

# Fetch subjects
curl http://localhost:8081/subjects

# Hard Delete Subject:
curl -X DELETE "http://localhost:8081/subjects/mysql-users-value?permanent=true"
```

---
Very Important Note — JDBC Source Initial Read Behavior

JDBC Source does not guarantee a snapshot of existing data.

Even with no stored offsets, it may:
- Initialize to current MAX(id)
- Skip existing rows
- Only emit new inserts

This behavior:
- Is by design
- Is not deterministic
- Is unrelated to serialization format

For reliable snapshots and change streams:
Use CDC (Change Data Capture) tools like **Debezium**

---
**JSON Schema Limitation with FileStreamSource**

FileStreamSourceConnector emits plain strings, not structured objects.

As a result:
- Schema Registry cannot validate JSON fields
- JSON Schema enforcement does not work

Schema Registry requires structured sources:
- JDBC
- Debezium
- REST Proxy
- Custom connectors

---

**Mental Model to Remember**
- Kafka stores bytes
- Schema Registry stores contracts
- Converters enforce correctness
- Kafka Connect works on Struct + Schema
- JDBC Source is best-effort polling, not CDC

FileStream connectors are for learning only.
For production, always use structured and deterministic sources.

