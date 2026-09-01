FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /build

# Resolve dependencies first for better layer caching.
COPY pom.xml ./
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B -q package -DskipTests

FROM eclipse-temurin:25-jre

# curl backs the container healthcheck in docker-compose.yml.
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && apt-get clean \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app
COPY --from=build /build/target/*.jar app.jar

EXPOSE 8080

# Let the JVM size its heap from the container limit rather than the host's RAM.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
