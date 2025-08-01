FROM openjdk:24-slim
ARG JAR_FILE=target/BackendAI-0.0.1-SNAPSHOT.jar
COPY ${JAR_FILE} app.jar
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app.jar"]
