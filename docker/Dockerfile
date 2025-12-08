# Stage 1: fetch jars from Apache Kafka image matching your version
FROM apache/kafka:3.7.0 AS kafka-dist

# build-time root to install plugins and copy files
USER root

# Where we'll place FileStream connector JARs
ENV FILESTREAM_PLUGIN_DIR=/usr/share/filestream-connectors
RUN mkdir -p ${FILESTREAM_PLUGIN_DIR}

# COPY the FileStream connector jars from the kafka image
# NOTE: verify the source path in the apache/kafka image; common is /opt/kafka/libs
COPY --from=kafka-dist /opt/kafka/libs/connect-file-*.jar ${FILESTREAM_PLUGIN_DIR}/

# Ensure plugin directories are readable by the Connect runtime user
RUN chmod -R 755 ${FILESTREAM_PLUGIN_DIR} || true

# Switch back to the non-root user used by the base image
USER appuser