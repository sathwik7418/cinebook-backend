
# Build stage: Java 21 + Maven
FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /app

COPY pom.xml ./

# Cache dependency resolution as a separate layer
RUN mvn -B dependency:go-offline

COPY src ./src

RUN mvn -B package -DskipTests

# Runtime stage: Java 21 only
FROM eclipse-temurin:21-jre

WORKDIR /app

COPY --from=build \
  /app/target/cinebook-1.0.0-SNAPSHOT.jar \
  /app/app.jar

EXPOSE 10000

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
