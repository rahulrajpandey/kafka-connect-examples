Exercise 1: FileStream Source & Sink Connectors (Kafka Connect)

This exercise demonstrates how to use FileStream Source and FileStream Sink connectors to read data from files and write it to Kafka topics, and then read from Kafka topics and write back to files.
All steps are included so learners can reproduce the setup without confusion.

⸻

1. Custom Kafka Connect Dockerfile (With FileStream Connectors)

Create a Dockerfile inside the docker directory:

FROM confluentinc/cp-kafka-connect:7.7.0

USER root

# Create plugin directory
RUN mkdir -p /usr/share/filestream-connectors

# Download and extract Apache Kafka libs containing FileStream connectors
RUN curl -L -o kafka.tgz https://archive.apache.org/dist/kafka/3.7.0/kafka_2.13-3.7.0.tgz && \
tar -xzf kafka.tgz && \
cp kafka_2.13-3.7.0/libs/connect-file-*.jar /usr/share/filestream-connectors/ && \
rm -rf kafka.tgz kafka_2.13-3.7.0

USER appuser

Make sure the Kafka Connect container includes:

CONNECT_PLUGIN_PATH=/usr/share/java,/usr/share/filestream-connectors


⸻

2. Create Source and Sink Files

On the host machine:

mkdir -p connectors/source connectors/sink
touch connectors/source/source.txt
touch connectors/sink/sink.txt
chmod -R 777 connectors

Verify inside Kafka Connect container:

docker exec -it kafka-connect ls -l /tmp/source
docker exec -it kafka-connect ls -l /tmp/sink


⸻

3. Create Kafka Topics

Source demo topic:

docker exec -it kafka-broker kafka-topics \
--create --topic file-source-topic \
--bootstrap-server kafka-broker:19092

Sink demo topic:

docker exec -it kafka-broker kafka-topics \
--create --topic file-sink-topic \
--bootstrap-server kafka-broker:19092

List all topics:

docker exec -it kafka-broker kafka-topics \
--list --bootstrap-server kafka-broker:19092


⸻

4. Register FileStream Source Connector

Reads from /tmp/source/source.txt and writes to file-source-topic.

curl -X POST http://localhost:8083/connectors \
-H "Content-Type: application/json" \
-d '{
"name": "file-source-connector",
"config": {
"connector.class": "org.apache.kafka.connect.file.FileStreamSourceConnector",
"tasks.max": "1",
"file": "/tmp/source/source.txt",
"topic": "file-source-topic",
"key.converter": "org.apache.kafka.connect.storage.StringConverter",
"value.converter": "org.apache.kafka.connect.storage.StringConverter"
}
}'

Check status:

curl -s localhost:8083/connectors/file-source-connector/status | jq


⸻

5. Register FileStream Sink Connector

Reads from Kafka topic file-sink-topic and writes to /tmp/sink/sink.txt.

curl -X POST http://localhost:8083/connectors \
-H "Content-Type: application/json" \
-d '{
"name": "file-sink-connector",
"config": {
"connector.class": "org.apache.kafka.connect.file.FileStreamSinkConnector",
"tasks.max": "1",
"topics": "file-sink-topic",
"file": "/tmp/sink/sink.txt",
"key.converter": "org.apache.kafka.connect.storage.StringConverter",
"value.converter": "org.apache.kafka.connect.storage.StringConverter"
}
}'

Check status:

curl -s localhost:8083/connectors/file-sink-connector/status | jq


⸻

6. Test Source Connector (File → Kafka)

Append lines to the file:

echo "hello world" >> connectors/source/source.txt
echo "kafka connect test" >> connectors/source/source.txt

Consume from topic:

docker exec -it kafka-broker kafka-console-consumer \
--topic file-source-topic \
--bootstrap-server kafka-broker:19092 \
--from-beginning

Expected output:

hello world
kafka connect test

![Source Connector Demo](Source%20Connector%20Demo.png)
⸻

7. Test Sink Connector (Kafka → File)

Produce messages:

docker exec -it kafka-broker kafka-console-producer \
--topic file-sink-topic \
--bootstrap-server kafka-broker:19092

Enter:

line-1
line-2
line-3

Check sink file on host:

tail -f connectors/sink/sink.txt

Expected:

line-1
line-2
line-3

![Sink Connector Demo](Sink%20Connector%20demo.png)
⸻

8. End-to-End Pipeline Example

Flow:

source.txt → Source Connector → file-pipeline-topic → Sink Connector → output.txt

Create topic:

docker exec -it kafka-broker kafka-topics \
--create --topic file-pipeline-topic \
--bootstrap-server kafka-broker:19092

Register Source:

curl -X POST http://localhost:8083/connectors \
-H "Content-Type: application/json" \
-d '{
"name": "file-pipeline-source",
"config": {
"connector.class": "org.apache.kafka.connect.file.FileStreamSourceConnector",
"tasks.max": "1",
"file": "/tmp/source/source.txt",
"topic": "file-pipeline-topic",
"key.converter": "org.apache.kafka.connect.storage.StringConverter",
"value.converter": "org.apache.kafka.connect.storage.StringConverter"
}
}'

Register Sink:

curl -X POST http://localhost:8083/connectors \
-H "Content-Type: application/json" \
-d '{
"name": "file-pipeline-sink",
"config": {
"connector.class": "org.apache.kafka.connect.file.FileStreamSinkConnector",
"tasks.max": "1",
"topics": "file-pipeline-topic",
"file": "/tmp/sink/output.txt",
"key.converter": "org.apache.kafka.connect.storage.StringConverter",
"value.converter": "org.apache.kafka.connect.storage.StringConverter"
}
}'

Append lines to trigger flow:

echo "hello" >> connectors/source/source.txt
echo "kafka-connect" >> connectors/source/source.txt
echo "pipeline test" >> connectors/source/source.txt

Validate Kafka messages:

docker exec -it kafka-broker kafka-console-consumer \
--topic file-pipeline-topic \
--bootstrap-server kafka-broker:19092 \
--from-beginning \
--timeout-ms 10000

Validate sink output:

tail -f connectors/sink/output.txt

![E2E Pipeline Demo](pipeline-demo.png)

⸻

9. Useful Kafka Connect REST APIs

List connectors:

curl -s localhost:8083/connectors | jq

Get connector config:

curl -s localhost:8083/connectors/<name> | jq

Get status:

curl -s localhost:8083/connectors/<name>/status | jq

Pause connector:

curl -X PUT localhost:8083/connectors/<name>/pause

Resume:

curl -X PUT localhost:8083/connectors/<name>/resume

Restart connector:

curl -X POST localhost:8083/connectors/<name>/restart

Restart task:

curl -X POST localhost:8083/connectors/<name>/tasks/0/restart

List only source connectors:

curl -s localhost:8083/connectors \
| jq -r '.[]' \
| xargs -I {} curl -s localhost:8083/connectors/{}/ \
| jq 'select(.type == "source") | .name'


⸻

10. Cleanup

curl -X DELETE http://localhost:8083/connectors/file-source-connector
curl -X DELETE http://localhost:8083/connectors/file-sink-connector
curl -X DELETE http://localhost:8083/connectors/file-pipeline-source
curl -X DELETE http://localhost:8083/connectors/file-pipeline-sink


⸻

11. Next Exercise: SMT (Single Message Transforms)
