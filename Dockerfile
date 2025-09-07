
FROM mcr.microsoft.com/playwright/java:v1.40.0-focal

WORKDIR /app

ARG JAR_FILE=target/BackendAI-0.0.1-SNAPSHOT.jar
COPY ${JAR_FILE} app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
