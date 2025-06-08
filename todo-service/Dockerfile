FROM openjdk:21-jre-slim
VOLUME /tmp
COPY target/todo-service-*.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java","-Djava.security.egd=file:/dev/./urandom","-jar","/app.jar"]