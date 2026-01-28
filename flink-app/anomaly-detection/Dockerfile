FROM flink:2.2.0-java17

# Install S3 filesystem plugin by downloading it
RUN mkdir -p /opt/flink/plugins/s3-fs-hadoop && \
    cd /opt/flink/plugins/s3-fs-hadoop && \
    wget https://repo1.maven.org/maven2/org/apache/flink/flink-s3-fs-hadoop/2.2.0/flink-s3-fs-hadoop-2.2.0.jar && \
    ls -la /opt/flink/plugins/s3-fs-hadoop/

# Copy your application JAR
COPY target/clickstream-anomaly-detection-2.0-SNAPSHOT.jar /opt/flink/usrlib/anomaly-detection.jar

# Copy application properties
COPY application.properties /opt/flink/conf/application.properties

# Install AWS MSK IAM auth library (already in your JAR via maven-shade-plugin)
# No additional setup needed since it's shaded into your JAR

