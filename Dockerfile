# syntax=docker/dockerfile:1

# Stage 1: Build the application
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /workspace

# Copy Maven wrapper / POM first for caching dependencies
COPY pom.xml .
RUN ./mvnw dependency:go-offline -B 2>/dev/null || true

# Copy source code and build package skipping tests in docker build
COPY src ./src
RUN apk add --no-cache maven && mvn clean package -DskipTests

# Stage 2: Minimal runtime image
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Run as non-root user for security best practice
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

COPY --from=builder /workspace/target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
