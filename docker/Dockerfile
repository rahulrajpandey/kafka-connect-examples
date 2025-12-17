# ------------------------------------------------------------
# Stage 1: Extract FileStream connector JARs from Apache Kafka
# ------------------------------------------------------------
FROM apache/kafka:3.7.0 AS kafka-dist

# ------------------------------------------------------------
# Stage 2: Build final Kafka Connect worker image
# ------------------------------------------------------------
FROM confluentinc/cp-kafka-connect:7.7.0

USER root

# ------------------------------------------------------------
# Create plugin directories (each plugin MUST be isolated)
# ------------------------------------------------------------
ENV FILESTREAM_PLUGIN_DIR=/usr/share/connect/plugins/filestream
ENV REGEXMASK_PLUGIN_DIR=/usr/share/connect/plugins/regex-mask

RUN mkdir -p ${FILESTREAM_PLUGIN_DIR} \
           ${REGEXMASK_PLUGIN_DIR}

# ------------------------------------------------------------
# Copy FileStream connector jars from the Kafka image
# ------------------------------------------------------------
COPY --from=kafka-dist /opt/kafka/libs/connect-file-*.jar ${FILESTREAM_PLUGIN_DIR}/

# ------------------------------------------------------------
# Copy custom SMT plugin
# ------------------------------------------------------------
COPY ./plugins/regex-mask/transforms-1.0-SNAPSHOT.jar \
     ${REGEXMASK_PLUGIN_DIR}/transforms-1.0-SNAPSHOT.jar

# ------------------------------------------------------------
# Install JDBC Connector via Confluent Hub
# ------------------------------------------------------------

RUN confluent-hub install --no-prompt confluentinc/kafka-connect-jdbc:10.7.6
# installs under /usr/share/confluent-hub-components/

# ------------------------------------------------------------
# Add MySQL JDBC Driver to JDBC plugin
# ------------------------------------------------------------
RUN curl -fL -o \
  /usr/share/confluent-hub-components/confluentinc-kafka-connect-jdbc/mysql-connector-j-8.3.0.jar \
  https://repo1.maven.org/maven2/com/mysql/mysql-connector-j/8.3.0/mysql-connector-j-8.3.0.jar

# ------------------------------------------------------------
# Set correct permissions ONLY on plugin folders
# ------------------------------------------------------------
RUN chmod -R 755 /usr/share/connect/plugins

# ------------------------------------------------------------
# Optimize Kafka Connect plugin discovery
# ------------------------------------------------------------
ENV CONNECT_PLUGIN_PATH="/usr/share/connect/plugins"
ENV CONNECT_PLUGIN_DISCOVERY="only_scan"

# Return to default non-root user
USER appuser