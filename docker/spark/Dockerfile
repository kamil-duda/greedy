# The Spark image of the local stack: the PostgreSQL JDBC driver is on the classpath of every process (Connect driver
# and executors), so no job resolves --packages at start-up. Plain ADD + sha256sum, no BuildKit-only syntax: the
# Testcontainers builder is the classic one.
FROM apache/spark:4.3.0-scala2.13-java21-ubuntu

USER root
ADD https://repo1.maven.org/maven2/org/postgresql/postgresql/42.7.14/postgresql-42.7.14.jar /opt/spark/jars/
RUN echo "73914527305a40cce504b0d3d90b23caf565136912d607ae8ae7c5895512332c  /opt/spark/jars/postgresql-42.7.14.jar" \
		| sha256sum -c - \
	&& chown spark:spark /opt/spark/jars/postgresql-42.7.14.jar \
	&& chmod 644 /opt/spark/jars/postgresql-42.7.14.jar
USER spark
