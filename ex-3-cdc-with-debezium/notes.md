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


---

## Examples – CDC with Debezium (MySQL)

### Ex 1. CDC + Schema Registry
### Ex 2. Single Table → Single Sink
### Ex 3. Multiple Tables → Single Sink
### Ex 4. Fan-out patterns
### Ex 5. Deletes, updates, and tombstones
### Ex 6. Common pitfalls and mental models