FROM openjdk:21-jre-slim
VOLUME /tmp
COPY target/auth-service-*.jar app.jar
EXPOSE 8083
ENTRYPOINT ["java","-Djava.security.egd=file:/dev/./urandom","-jar","/app.jar"]