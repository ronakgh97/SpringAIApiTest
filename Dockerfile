# --- Build Stage ---
FROM maven:3.9.6-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

# --- Runtime Stage (with Playwright Java) ---
FROM mcr.microsoft.com/playwright/java:v1.40.0-focal
WORKDIR /app
COPY --from=build /app/target/*SNAPSHOT.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]