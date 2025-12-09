# ------------------------------------------------------------
# Stage 1: extract FileStream connector jars from Apache Kafka
# ------------------------------------------------------------
FROM apache/kafka:3.7.0 AS kafka-dist


# ------------------------------------------------------------
# Stage 2: final Kafka Connect image with plugins
# ------------------------------------------------------------
FROM confluentinc/cp-kafka-connect:7.7.0

USER root

# Install unzip for extracting FilePulse plugin (UBI8 → microdnf)
RUN microdnf install -y unzip && microdnf clean all

# Directory for FileStream connector jars
ENV FILESTREAM_PLUGIN_DIR=/usr/share/filestream-connectors
RUN mkdir -p ${FILESTREAM_PLUGIN_DIR}

# Copy FileStream connector jars from Stage 1
COPY --from=kafka-dist /opt/kafka/libs/connect-file-*.jar ${FILESTREAM_PLUGIN_DIR}/

# custom SMT
COPY ./plugins/regex-mask/transforms-1.0-SNAPSHOT.jar /usr/share/java/regex-mask/transforms-1.0-SNAPSHOT.jar

# Permissions
RUN chmod -R 755 /usr/share/

USER appuser