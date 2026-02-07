# Stage 1: Build
FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /app

# Copy pom and proto files first to cache dependencies
COPY pom.xml .
COPY src/main/proto ./src/main/proto

# Download dependencies and compile proto (this layer will be cached unless pom.xml or proto changes)
RUN mvn dependency:go-offline -DskipTests && \
    mvn protobuf:compile -DskipTests

# Copy rest of source and build (only this layer rebuilds when code changes)
COPY src ./src
RUN mvn package -DskipTests -Dprotobuf.skip=true -o

# Stage 2: Runtime
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Create non-root user for security
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
RUN mkdir -p /app/logs && chown appuser:appgroup /app/logs
USER appuser

# Copy JAR from builder
COPY --from=builder /app/target/pms-trade-capture-*.jar app.jar

# Configuration for JVM inside container
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"

# Expose Port
EXPOSE 8082

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]