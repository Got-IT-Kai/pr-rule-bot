# Multi-stage Dockerfile for all microservices
# Usage: docker build --build-arg SERVICE_NAME=webhook-service --build-arg SERVICE_PORT=8080 -t webhook-service .

# Stage 1: Build stage
FROM gradle:8.11.1-jdk21-alpine AS builder

ARG SERVICE_NAME
WORKDIR /app

# Copy Gradle wrapper and build files first (for caching)
COPY gradle gradle/
COPY gradlew build.gradle.kts settings.gradle gradle.properties ./
COPY gradle/verification-metadata.xml gradle/

# Copy shared modules
COPY platform-commons/ platform-commons/
COPY shared-events/ shared-events/

# Copy all service modules
COPY webhook-service/ webhook-service/
COPY context-service/ context-service/
COPY review-service/ review-service/
COPY integration-service/ integration-service/

# Build the specific service
RUN ./gradlew :${SERVICE_NAME}:bootJar --no-daemon --stacktrace

# Stage 2: Runtime stage
FROM eclipse-temurin:21-jre-alpine

# Install curl for health checks
RUN apk add --no-cache curl

ARG SERVICE_NAME
ARG SERVICE_PORT

WORKDIR /app

# Copy the built JAR from builder stage
COPY --from=builder /app/${SERVICE_NAME}/build/libs/*.jar app.jar

# Expose the service port
EXPOSE ${SERVICE_PORT}

# Add non-root user for security
RUN addgroup -g 1000 appuser && \
    adduser -D -u 1000 -G appuser appuser && \
    chown -R appuser:appuser /app

USER appuser

# Java options for containerized environment
ENV JAVA_OPTS="-XX:+UseContainerSupport \
    -XX:MaxRAMPercentage=75.0 \
    -XX:+UseG1GC \
    -XX:+DisableExplicitGC \
    -Djava.security.egd=file:/dev/./urandom"

# Health check
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:${SERVICE_PORT}/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
