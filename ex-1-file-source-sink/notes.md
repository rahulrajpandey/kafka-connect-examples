Exercise 1: FileStream Source & Sink Connectors (Kafka Connect)

This exercise demonstrates how to use FileStream Source and FileStream Sink connectors to read data from files and write it to Kafka topics, and then read from Kafka topics and write back to files.
All steps are included so learners can reproduce the setup reliably.

⸻

1. Custom Kafka Connect Dockerfile (With FileStream Connectors)

Create a Dockerfile inside the docker directory:
Refer kafka-connect-examples/docker/Dockerfile.


⸻

2. Create Source and Sink Files

On the host machine:

mkdir -p connectors/source connectors/sink
touch connectors/source/source.txt
touch connectors/sink/sink.txt
chmod -R 777 connectors

Verify inside Kafka Connect:

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

List topics:

docker exec -it kafka-broker kafka-topics \
--list --bootstrap-server kafka-broker:19092


⸻

4. Register FileStream Source Connector

Reads from /tmp/source/source.txt and writes to file-source-topic:

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

Reads from file-sink-topic and writes to /tmp/sink/sink.txt:

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

Status:

curl -s localhost:8083/connectors/file-sink-connector/status | jq


⸻

6. Test Source Connector (File → Kafka)

Write lines to file:

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

Type:

line-1
line-2
line-3

Verify sink file:

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

Append data:

echo "hello" >> connectors/source/source.txt
echo "kafka-connect" >> connectors/source/source.txt
echo "pipeline test" >> connectors/source/source.txt

Consume / verify:

docker exec -it kafka-broker kafka-console-consumer \
--topic file-pipeline-topic \
--bootstrap-server kafka-broker:19092 \
--from-beginning \
--timeout-ms 10000

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

Pause:

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
curl -X DELETE http://localhost:8083/connectors/regex-source
curl -X DELETE http://localhost:8083/connectors/regex-sink
curl -X DELETE http://localhost:8083/connectors/mask-sink
curl -X DELETE http://localhost:8083/connectors/partial-mask-sink

Delete topics (optional, if allowed):

docker exec -it kafka-broker kafka-topics --bootstrap-server kafka-broker:19092 --delete --topic file-source-topic || true
docker exec -it kafka-broker kafka-topics --bootstrap-server kafka-broker:19092 --delete --topic file-sink-topic || true
docker exec -it kafka-broker kafka-topics --bootstrap-server kafka-broker:19092 --delete --topic file-pipeline-topic || true
docker exec -it kafka-broker kafka-topics --bootstrap-server kafka-broker:19092 --delete --topic file-raw || true


⸻

11. SMT (Single Message Transforms)

Example 1 — No Transformation (Simple Copy)

docker exec -it kafka-broker kafka-topics \
--create --topic file-raw \
--bootstrap-server kafka-broker:19092

Register source:

curl -X POST http://localhost:8083/connectors \
-H "Content-Type: application/json" \
-d '{
"name": "regex-source",
"config": {
"connector.class": "org.apache.kafka.connect.file.FileStreamSourceConnector",
"tasks.max": "1",
"file": "/tmp/source/source.txt",
"topic": "file-raw",
"key.converter": "org.apache.kafka.connect.storage.StringConverter",
"value.converter": "org.apache.kafka.connect.storage.StringConverter"
}
}'

Register SMT sink:

curl -X POST http://localhost:8083/connectors \
-H "Content-Type: application/json" \
-d '{
"name": "regex-sink",
"config": {
"connector.class": "org.apache.kafka.connect.file.FileStreamSinkConnector",
"tasks.max": "1",
"topics": "file-raw",
"file": "/tmp/sink/regex-output.txt",
"key.converter": "org.apache.kafka.connect.storage.StringConverter",
"value.converter": "org.apache.kafka.connect.storage.StringConverter",
"transforms": "route",
"transforms.route.type": "org.apache.kafka.connect.transforms.RegexRouter",
"transforms.route.regex": "file-raw",
"transforms.route.replacement": "file-processed"
}
}'

Test:

echo "Hello Simple SMT" >> connectors/source/source.txt
echo "It will be copied directly to sink file without any transformations" >> connectors/source/source.txt

tail -f connectors/sink/regex-output.txt

![Simple Transformation](Simple%20SMT%20Ex.png)

Clean:

curl -X DELETE http://localhost:8083/connectors/regex-sink



⸻

Example 2 — Masking (Full-Field Masking)

curl -X POST http://localhost:8083/connectors \
-H "Content-Type: application/json" \
-d '{
"name": "mask-sink",
"config": {
"connector.class": "org.apache.kafka.connect.file.FileStreamSinkConnector",
"tasks.max": "1",
"topics": "file-raw",
"file": "/tmp/sink/mask-output.txt",
"key.converter": "org.apache.kafka.connect.storage.StringConverter",
"value.converter": "org.apache.kafka.connect.storage.StringConverter",
"transforms": "Hoist,Mask",
"transforms.Hoist.type": "org.apache.kafka.connect.transforms.HoistField$Value",
"transforms.Hoist.field": "message",
"transforms.Mask.type": "org.apache.kafka.connect.transforms.MaskField$Value",
"transforms.Mask.fields": "message",
"transforms.Mask.replacement": "*****"
}
}'

![Masking Demo](Message%20Mask%20SMT%20Ex.png)

Clean:

curl -X DELETE http://localhost:8083/connectors/mask-sink


⸻

12: Partial Masking (requires installing additional plugins)

Build Custom Transformer class and place the jar into docker/plugins/regex-mask directory.
Update Dockerfile to include this custom SMT plugin.

curl -s http://localhost:8083/connector-plugins | jq

docker exec -it kafka-connect bash -lc "jar tf /usr/share/java/regex-mask/transforms-1.0-SNAPSHOT.jar | grep RegexMask || true"
com/rrp/connect/RegexMask.class
com/rrp/connect/RegexMask$Value.class

docker logs kafka-connect | grep -i regex;

[2025-12-09 16:09:05,297] INFO Loading plugin from: /usr/share/java/regex-mask (org.apache.kafka.connect.runtime.isolation.PluginScanner)
[2025-12-09 16:09:05,303] INFO Registered loader: PluginClassLoader{pluginLocation=file:/usr/share/java/regex-mask/} (org.apache.kafka.connect.runtime.isolation.PluginScanner)
[2025-12-09 16:09:05,313] INFO Loading plugin from: /usr/share/java/regex-mask/transforms-1.0-SNAPSHOT.jar (org.apache.kafka.connect.runtime.isolation.PluginScanner)
[2025-12-09 16:09:05,319] INFO Registered loader: PluginClassLoader{pluginLocation=file:/usr/share/java/regex-mask/transforms-1.0-SNAPSHOT.jar} (org.apache.kafka.connect.runtime.isolation.PluginScanner)
[2025-12-09 16:09:13,529] INFO Loading plugin from: /usr/share/java/regex-mask (org.apache.kafka.connect.runtime.isolation.PluginScanner)
[2025-12-09 16:09:13,662] INFO Registered loader: PluginClassLoader{pluginLocation=file:/usr/share/java/regex-mask/} (org.apache.kafka.connect.runtime.isolation.PluginScanner)
[2025-12-09 16:09:13,663] INFO Loading plugin from: /usr/share/java/regex-mask/transforms-1.0-SNAPSHOT.jar (org.apache.kafka.connect.runtime.isolation.PluginScanner)
[2025-12-09 16:09:13,783] INFO Registered loader: PluginClassLoader{pluginLocation=file:/usr/share/java/regex-mask/transforms-1.0-SNAPSHOT.jar} (org.apache.kafka.connect.runtime.isolation.PluginScanner)
file:/usr/share/java/regex-mask/	com.rrp.connect.RegexMask	transformation	undefined
file:/usr/share/java/regex-mask/transforms-1.0-SNAPSHOT.jar	com.rrp.connect.RegexMask	transformationundefined
[2025-12-09 16:09:14,945] INFO Added plugin 'com.rrp.connect.RegexMask' (org.apache.kafka.connect.runtime.isolation.DelegatingClassLoader)
[2025-12-09 16:09:14,945] INFO Added plugin 'org.apache.kafka.connect.transforms.RegexRouter' (org.apache.kafka.connect.runtime.isolation.DelegatingClassLoader)
[2025-12-09 16:09:14,945] INFO Added plugin 'com.rrp.connect.RegexMask$Value' (org.apache.kafka.connect.runtime.isolation.DelegatingClassLoader)
[2025-12-09 16:09:14,947] INFO Added alias 'RegexRouter' to plugin 'org.apache.kafka.connect.transforms.RegexRouter' (org.apache.kafka.connect.runtime.isolation.DelegatingClassLoader)
[2025-12-09 16:09:14,947] INFO Added alias 'RegexMask' to plugin 'com.rrp.connect.RegexMask' (org.apache.kafka.connect.runtime.isolation.DelegatingClassLoader)


Validate SMT is loadable:
curl -s -X PUT http://localhost:8083/connector-plugins/org.apache.kafka.connect.file.FileStreamSinkConnector/config/validate \
-H "Content-Type: application/json" \
-d '{
"connector.class":"org.apache.kafka.connect.file.FileStreamSinkConnector",
"transforms":"mask",
"transforms.mask.type":"com.rrp.connect.RegexMask$Value",
"transforms.mask.regex":"([A-Za-z0-9._%+-]+)@",
"transforms.mask.replacement":"***@"
}' | jq


Create Source and Sink Connector and test

docker exec -it kafka-broker kafka-topics \
--create --topic file-raw \
--bootstrap-server kafka-broker:19092

Register source:

curl -X POST http://localhost:8083/connectors \
-H "Content-Type: application/json" \
-d '{
"name": "regex-source",
"config": {
"connector.class": "org.apache.kafka.connect.file.FileStreamSourceConnector",
"tasks.max": "1",
"file": "/tmp/source/source.txt",
"topic": "file-raw",
"key.converter": "org.apache.kafka.connect.storage.StringConverter",
"value.converter": "org.apache.kafka.connect.storage.StringConverter"
}
}'

curl -s -X POST http://localhost:8083/connectors \
-H "Content-Type: application/json" \
-d '{
"name": "file-mask-sink",
"config": {
"connector.class": "org.apache.kafka.connect.file.FileStreamSinkConnector",
"tasks.max": "1",
"topics": "file-raw",
"file": "/tmp/sink/regex-mask-output.txt",

    "key.converter": "org.apache.kafka.connect.storage.StringConverter",

    "value.converter": "org.apache.kafka.connect.storage.StringConverter",
    "value.converter.schemas.enable": "false",

    "transforms": "MaskEmail",
    "transforms.MaskEmail.type": "com.rrp.connect.RegexMask$Value",

    "transforms.MaskEmail.regex": "(?<=.{2}).(?=[^@]*@)",
    "transforms.MaskEmail.replacement": "*"
}
}'

OUTPPUT:
{"name":"file-mask-sink","config":{"connector.class":"org.apache.kafka.connect.file.FileStreamSinkConnector",
"tasks.max":"1","topics":"file-raw","file":"/tmp/sink/regex-mask-output.txt",
"key.converter":"org.apache.kafka.connect.storage.StringConverter",
"value.converter":"org.apache.kafka.connect.json.JsonConverter","value.converter.schemas.enable":"false",
"transforms":"MaskEmail","transforms.MaskEmail.type":"com.rrp.connect.RegexMask$Value",
"transforms.MaskEmail.regex":"(?<=.{2}).(?=[^@]*@)",
"transforms.MaskEmail.replacement":"*","name":"file-mask-sink"},"tasks":[],"type":"sink"}

TEST:
Write to Source file:
echo "Hello No Regex Transformation needed" >> connectors/source/source.txt
echo "Transform this rahul@test.com" >> connectors/source/source.txt
echo "rahul.raj@example.com" >> connectors/source/source.txt
echo '{"email": "rahul.raj@example.com", "name": "Rahul"}' >> connectors/source/source.txt
echo '{"profile": {"email": "john.doe@company.org", "age": 30}}' >> connectors/source/source.txt

> tail -f connectors/sink/regex-mask-output.txt
Hello No Regex Transformation needed
Transform this ***@test.com
Transform this again ***@test.com
ra*******@example.com
{"email":"ra*******@example.com","name":"Rahul"}
{"profile":{"email":"jo******@company.org","age":30}}


![Partial Masking Demo](Partial-Masking-Demo.png)

CleanUP:
curl -X DELETE http://localhost:8083/connectors/regex-source

curl -X DELETE http://localhost:8083/connectors/file-mask-sink

