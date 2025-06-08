FROM openjdk:21-jre-slim
VOLUME /tmp
COPY target/gateway-service-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java","-Djava.security.egd=file:/dev/./urandom","-jar","/app.jar"]
