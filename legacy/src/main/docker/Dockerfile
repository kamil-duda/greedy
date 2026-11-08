FROM openjdk:8-jre-alpine

MAINTAINER Kamil Duda <kamilduda01@gmail.com>

VOLUME /tmp

# Add application .jar file
ADD greedy-0.0.1.jar app.jar
RUN sh -c 'touch /app.jar'

# Additional Java options
ENV JAVA_OPTS=""

ENTRYPOINT [ "sh", "-c", "java $JAVA_OPTS -Djava.security.egd=file:/dev/./urandom -jar /app.jar" ]
