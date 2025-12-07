# Base image: Confluent Kafka Connect 7.7.0
FROM confluentinc/cp-kafka-connect:7.7.0

# Switch to root for installation steps
USER root

# Directory where FileStream connector JARs will be placed
ENV FILESTREAM_PLUGIN_DIR=/usr/share/filestream-connectors

# Create plugin directory
RUN mkdir -p ${FILESTREAM_PLUGIN_DIR}

# Apache Kafka version that matches your Kafka cluster (3.7.0 for Kafka 7.7.x)
ARG KAFKA_VERSION=3.7.0
ARG SCALA_VERSION=2.13

# Download only once, extract only needed JARs, keep image small
RUN curl -L -o /tmp/kafka.tgz \
      https://archive.apache.org/dist/kafka/${KAFKA_VERSION}/kafka_${SCALA_VERSION}-${KAFKA_VERSION}.tgz && \
    tar -xzf /tmp/kafka.tgz -C /tmp && \
    cp /tmp/kafka_${SCALA_VERSION}-${KAFKA_VERSION}/libs/connect-file-*.jar ${FILESTREAM_PLUGIN_DIR}/ && \
    rm -rf /tmp/kafka*   # cleanup extracted tarball and folder to keep image lean

# Switch back to non-root user (required)
USER appuser