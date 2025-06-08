FROM openjdk:21-jre-slim
VOLUME /tmp
COPY target/user-service-*.jar app.jar
EXPOSE 8082
ENTRYPOINT ["java","-Djava.security.egd=file:/dev/./urandom","-jar","/app.jar"]