## Fundamental
## 1. What is CDC (Change Data Capture)?

**The core problem CDC solves**

Traditional database integrations ask: “What is the current state of the table?”

CDC asks a different question: “What exactly changed, in what order, and why?”

_CDC captures changes, not snapshots._ CDC does not query tables rather it reads the DB's own change log.

Changes include:
- INSERT
- UPDATE
- DELETE
- schema changes

And crucially:
- In the exact order they happened
- With transaction boundaries
- Without polling the table
- Near real-time
- Very low load on DB

## 2. How CDC works internally 
Every serious database maintains a write-ahead log:

For ex:

| Database   | Log |  
|------------|-----|  
| MySQL      | binlog |  
| Postgres   | WAL |  
| Oracle     | redo logs |  
| SQL Server | transaction log |

CDC tools:
- Attach to this log
- Parse changes
- Reconstruct row-level events

This means:
- No table scans
- No missed updates
- No race conditions

## 3. Where Debezium fits

Debezium is:
- A CDC engine
- Implemented as Kafka Connect source connectors
- Log-based (not polling)
- Schema-aware
- Built for Kafka

Debezium’s job: “Turn database changes into a reliable event stream.”

**What Debezium produces** 
- Debezium does not emit plain rows.
- It emits events with context.

Conceptual shape (simplified):
```
{
  "before": { ... },
  "after": { ... },
  "source": {
    "db": "demo",
    "table": "users",
    "lsn": "...",
    "ts_ms": 123456
  },
  "op": "c",
  "ts_ms": 123456
}
```
This is called an envelope, and it gives great sort of information:
- You know what changed
- You know where it came from
- You know how it changed

## 4. Snapshot + streaming 
When Debezium starts:

**Phase 1: Snapshot**
- Reads all existing rows once
- Emits them as op = r (read)
- Guarantees consistency

**Phase 2: Streaming**
- Switches to binlog
- Emits: 
i) c → insert
ii) u → update
iii) d → delete

---
## Setup 
MySQL requirements for CDC:

Debezium reads MySQL binlog, not tables.
So MySQL must be configured correctly.

**Required MySQL settings**
```
server-id=1             # MySQL replication identity
log-bin=mysql-bin       # enables binlog
binlog-format=ROW       # row-level changes
binlog-row-image=FULL   # full before/after images
```
### 1. Update MySQL Service in docker-compose.


**Recreate and restart MySQL Service**
```
docker-compose stop mysql
docker-compose build --no-cache mysql
docker-compose up -d mysql
```

### 2. Validation

Enter MySQL Service and Verify binlog is enabled
```
# for administrative setup, login using root user
docker exec -it mysql mysql -uroot -proot

SHOW VARIABLES LIKE 'log_bin';

SHOW VARIABLES LIKE 'binlog_format';
```
![MySQL Binlog Validation](MySQL-binlog-validation.png)

### 3. Create Debezium user & grant permissions

Debezium needs replication-level privileges.
Why these permissions:
- REPLICATION SLAVE / CLIENT → read binlog
- SELECT → snapshot phase
- RELOAD → binlog metadata
- SHOW DATABASES → schema discovery

```
CREATE USER 'debezium'@'%' IDENTIFIED BY 'dbz';

GRANT SELECT, RELOAD, SHOW DATABASES, REPLICATION SLAVE, REPLICATION CLIENT ON *.* TO 'debezium'@'%';

FLUSH PRIVILEGES;
```
![MySQL-debezium-user-created](MySQL-debezium-user-created.png)


### 4. Install Debezium MySQL connector plugin
Debezium runs inside Kafka Connect.

**Update the Kafka Connect Dockerfile**
```
RUN confluent-hub install --no-prompt debezium/debezium-connector-mysql:2.6.1
```

**Rebuild and Restart kafka-connect service**
```
docker-compose stop kafka-connect
docker-compose build --no-cache kafka-connect
docker-compose up -d kafka-connect
```

**Verify Debezium plugin is loaded**
```
curl http://localhost:8083/connector-plugins | jq
```

Expected
```
{
  "class": "io.debezium.connector.mysql.MySqlConnector",
  "type": "source",
  "version": "3.1.2.Final"
}
```
![MySQL-debezium-connector](MySQL-debezium-connector.png)

### 5. Setup Redpanda Console for Kafka-UI Access
We need to setup some UI to see messages properly from kafka topics if those are Avro serialized.
In this example, we will setup Redpanda Console for this use case and will enable and configure SchemaRegistry in that so that it can properly parse the messages in topic and display.

Create Config file for Redpanda:
```
kafka:
  brokers:
    - kafka-broker:19092

schemaRegistry:
  enabled: true
  urls:
    - http://schema-registry:8081
```

And then add redpanda-console service in docker-compose file and start the service.

### 6. Validate Schema Registry is reachable
```
curl http://localhost:8081/subjects
```

---

## Examples – CDC with Debezium (MySQL)

### Ex 1. Single Table → Single Sink

**Step 1: Ensure the source table exists**
```
docker exec -it mysql mysql -u demo -pdemo demo

drop table if exists users;

CREATE TABLE users (
  id INT PRIMARY KEY,
  name VARCHAR(255),
  age INT,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO users (id, name, age) VALUES
(1, 'Rahul', 30),
(2, 'Amit', 28);

# This initial insertion will be used for snapshot step on Debezium start.
```

**Step 2: topic naming pattern decision**
```
<topic.prefix>.<database>.<table>

Ex: dbserver1.demo.users
```

**Step 3: Create the Debezium MySQL connector**

We have setup `auto.create.topics.enable=false` in kafka broker, so have to create topic pre-hand and then register the connector.

**Create topics**

```
# Create the heartbeat topic for Debezium (topic.prefix)
docker exec -it kafka-broker kafka-topics \
  --bootstrap-server kafka-broker:19092 \
  --create \
  --topic dbserver1 \
  --partitions 1 \
  --replication-factor 1

# create source topic
docker exec -it kafka-broker kafka-topics \
  --bootstrap-server kafka-broker:19092 \
  --create \
  --topic dbserver1.demo.users \
  --partitions 1 \
  --replication-factor 1
  
docker exec -it kafka-broker \
  kafka-topics --bootstrap-server kafka-broker:19092 --list
```

Register the connector
```
curl -X POST http://localhost:8083/connectors \
  -H "Content-Type: application/json" \
  -d '{
    "name": "debezium-mysql-users",
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
      "table.include.list": "demo.users",

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
![Debezium-Connector-Registration](Debezium-Connector-Registration.png)

**Step 4: Verify Connector Status**
```
curl http://localhost:8083/connectors/debezium-mysql-users/status | jq
```

**Step 5: Observe snapshot phase**
```
docker logs kafka-connect | grep -i snapshot
```
![snapshot-logs](snapshot-logs.png)

In the attached screenshot, we can see messages indicating:
- Snapshot started
- Snapshot completed

**Step 6: Verify Subject Creation**

```
curl http://localhost:8081/subjects

Expected: ["dbserver1.demo.users-value"]
```

Step 7: Consume from topic
```
docker exec -it kafka-broker kafka-console-consumer \
  --bootstrap-server kafka-broker:19092 \
  --topic dbserver1.demo.users \
  --from-beginning
```
![Data Consumption](Data-Consumed-from-Topic.png)

Step 8: Check decoded messages from topic on Redpanda-Console Web UI
![Checking-Messages-Redpanda-Console-UI](Checking-Messages-Redpanda-Console-UI.png)
![Decoded-Message-Payload-Redpanda-Console-UI](Decoded-Message-Payload-Redpanda-Console-UI.png)

**Decoded value, this is a Debezium Change Event Envelope, not a row.**

This envelope is for CDC pipelines, not business APIs.
```
{
    "after": {
        "dbserver1.demo.users.Value": {
            "age": 30,
            "created_at": "2025-12-17T16:39:26Z",
            "id": 1,
            "name": "Rahul"
        }
    },
    "before": null,
    "op": "r",
    "source": {
        "connector": "mysql",
        "db": "demo",
        "file": "mysql-bin.000001",
        "gtid": null,
        "name": "dbserver1",
        "pos": 2276,
        "query": null,
        "row": 0,
        "sequence": null,
        "server_id": 0,
        "snapshot": "first",
        "table": "users",
        "thread": null,
        "ts_ms": 1765992367000,
        "ts_ns": "1765992367000000000",
        "ts_us": "1765992367000000",
        "version": "3.1.2.Final"
    },
    "transaction": null,
    "ts_ms": 1765992367019,
    "ts_ns": "1765992367019835467",
    "ts_us": "1765992367019835"
}
```

**Understanding Debezium envelope**
The event has these major sections:
```
{
  "before": null,
  "after": {...},
  "op": "r",
  "source": {...},
  "transaction": null,
  "ts_ms": ...
}
```
i) op — the most important field

| op | Meaning | When it appers          | 
|----|---------|-------------------------|
| r  | Read    | Snapshot (initial load) |
| c  | Create  | INSERT                  |
| u  | Update  | UPDATE                  |
| d  | Delete  | DELETE                  |

ii) before and after

- before → row state before the change
- after → row state after the change

For snapshots and inserts:
- before = null
- after = full row

For updates:
- before = old row
- after = new row

For deletes:
- before = last row
- after = null

iii) source — metadata
```
"source": {
  "connector": "mysql",
  "db": "demo",
  "table": "users",
  "name": "dbserver1",
  "file": "mysql-bin.000001",
  "pos": 2276,
  "snapshot": "first",
  "version": "3.1.2.Final"
}
```
This allows:
- Auditing
- Replay
- Debugging corruption
- Multi-table routing
- Multi-DB pipelines

`"snapshot": "first"` - This row came from the initial snapshot, not from live binlog changes. Later, for new inserts/updates, this field will be false.

**Step 9: Sink Connector for table-level replication semantics**
We want to have a replica table where all the operations of source table is replicated, kind of logical replication, target table always converges to source table state.
For that, we must unwrap/transform the `after` field, convert deletes into DELETE statements and must use upsert semantics.
For unwrapping or transformation, we will use ExtractNewRecordState SMT which turns CDC events into row-level mutations.

| CDC Event       | After SMT     | 
|-----------------|---------------|
| Snapshot (op=r) | INSERT row    |
| Insert (op=c)   | INSERT row    |
| Update (op=u)   | UPDATE row    |
| Delete (op=d)   | DELETE row    |

Create an identical table that mirrors the source:
We are using created_at field as VARCHAR because Debezium internally captures timestamps as logical types and created_at from users table becomes a String representation like: `ISO-8601 UTC timestamp` which essentially is timezone-safe, and portable.

```
docker exec -it mysql mysql -u demo -pdemo demo

CREATE TABLE users_replica (
  id INT PRIMARY KEY,
  name VARCHAR(255),
  age INT,
  created_at VARCHAR(40),
  __deleted VARCHAR(5)
);

```

**Step 10: JDBC Sink Connector (CDC → replica)**

<details>
<summary><strong>JDBC Sink Connector Config</strong></summary>

```curl
curl -X POST http://localhost:8083/connectors \
  -H "Content-Type: application/json" \
  -d '{
  "name": "mysql-users-cdc-replica-sink",
  "config": {
    "connector.class": "io.confluent.connect.jdbc.JdbcSinkConnector",
    "tasks.max": "1",

    "topics": "dbserver1.demo.users",

    "connection.url": "jdbc:mysql://mysql:3306/demo",
    "connection.user": "demo",
    "connection.password": "demo",

    "auto.create": "false",
    "auto.evolve": "false",

    "insert.mode": "upsert",
    "pk.mode": "record_value",
    "pk.fields": "id",

    "table.name.format": "users_replica",

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


**Step 11: Verify Connector Status**
```
curl http://localhost:8083/connectors/mysql-users-cdc-replica-sink/status | jq
```

Learning: 
i) In this case we tried with: `"transforms.unwrap.delete.handling.mode": "rewrite"`

It required one more field in sink table __deleted VARCHAR(5), as it emits __deleted="false" for non-delete operations.
If you don't want this field in DB then can use `"transforms.unwrap.delete.handling.mode": "drop"` but in that case will lose delete propagation.

ii) In this case we tried with: `"transforms.unwrap.drop.tombstones": "true"`, which for delete commands, does not actually delete the row in sink table rather just marks the __deleted column as `true`. 
This helps in replaying the events by other systems, but if you want to delete row from sink table for a delete in source table, then use these properties: 
```
"delete.enabled": "true",
"pk.mode": "record_key",

"transforms.unwrap.drop.tombstones": "false",
"transforms.unwrap.delete.handling.mode": "drop"

```
Ok, so lets try to demonstrate this behavior with the use of a new JDBC Sink Connector to write to a new table.

Create table: 

```
CREATE TABLE users_replica_v2 (
  id INT PRIMARY KEY,
  name VARCHAR(255),
  age INT,
  created_at VARCHAR(40)
);
```

<details>
<summary><strong>JDBC Sink Connector Config-v2</strong></summary>

```curl
curl -X POST http://localhost:8083/connectors \
  -H "Content-Type: application/json" \
  -d '{
  "name": "mysql-users-cdc-replica-sink-v2",
  "config": {
    "connector.class": "io.confluent.connect.jdbc.JdbcSinkConnector",
    "tasks.max": "1",

    "topics": "dbserver1.demo.users",

    "connection.url": "jdbc:mysql://mysql:3306/demo",
    "connection.user": "demo",
    "connection.password": "demo",

    "auto.create": "false",
    "auto.evolve": "false",

    "insert.mode": "upsert",
    "delete.enabled": "true",
    "pk.mode": "record_key",
    "pk.fields": "id",

    "table.name.format": "users_replica_v2",

    "key.converter": "io.confluent.connect.avro.AvroConverter",
    "key.converter.schema.registry.url": "http://schema-registry:8081",

    "value.converter": "io.confluent.connect.avro.AvroConverter",
    "value.converter.schema.registry.url": "http://schema-registry:8081",

    "transforms": "unwrap",

    "transforms.unwrap.type": "io.debezium.transforms.ExtractNewRecordState",

    "transforms.unwrap.drop.tombstones": "false",
    "transforms.unwrap.delete.handling.mode": "drop"
  }
}'
```
</details>



**Test:** 
```
docker exec -it mysql mysql -u demo -pdemo demo

SELECT * FROM users;

SELECT * FROM users_replica;

INSERT INTO users VALUES (10, 'A', 30)

UPDATE users SET age=31 WHERE id=10

INSERT INTO users (id, name, age) VALUES (20, 'CDC-Test', 40);

UPDATE users SET age=41 WHERE id=20;

DELETE FROM users WHERE id=20;
```


Cleanup: 
```
curl -X DELETE http://localhost:8083/connectors/debezium-mysql-users
curl -X DELETE http://localhost:8083/connectors/mysql-users-cdc-replica-sink
curl -X DELETE http://localhost:8083/connectors/mysql-users-cdc-replica-sink-v2

curl -X DELETE http://localhost:8081/subjects/dbserver1.demo.users-value

curl -X DELETE http://localhost:8081/subjects/dbserver1.demo.users-key

curl -X DELETE \
  "http://localhost:8081/subjects/dbserver1.demo.users-value?permanent=true"

curl -X DELETE \
  "http://localhost:8081/subjects/dbserver1.demo.users-key?permanent=true"

```

Let's try this exercise by flipping the connectors creation, first lets create sink connectors and then source connector. This pattern is used for Replicas, Cache warmup, Search Indexes.

--- 

### Ex 2. Multiple Tables → Single Sink





### Ex 3. Fan-out patterns





### Ex 4. Deletes, updates, and tombstones




### Ex 5. Common pitfalls and mental models


Useful Command:
1. Reset Consumer Group Offset (Make sure no active consumer group exists for that group)
```
docker exec -it kafka-broker kafka-consumer-groups \
  --bootstrap-server kafka-broker:19092 \
  --group <CG-Group-Name> \
  --reset-offsets \
  --to-earliest \
  --execute \
  --topic dbserver1.demo.users
```
