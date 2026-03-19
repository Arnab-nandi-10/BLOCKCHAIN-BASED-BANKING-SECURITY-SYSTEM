# =============================================================================
# Multi-stage build for blockchain-service
# Stage 1: Build with Maven
# Stage 2: Runtime with eclipse-temurin:21-jre-alpine
# =============================================================================

# ---------------------------------------------------------------------------
# Stage 1 — Build
# ---------------------------------------------------------------------------
FROM maven:3.9.6-eclipse-temurin-21-alpine AS build

WORKDIR /workspace

# Copy the parent POM and shared module first to leverage Docker layer caching.
COPY pom.xml ./pom.xml

# All module POM stubs — required for Maven multi-module reactor validation
COPY shared/pom.xml              shared/pom.xml
COPY api-gateway/pom.xml         api-gateway/pom.xml
COPY auth-service/pom.xml        auth-service/pom.xml
COPY tenant-service/pom.xml      tenant-service/pom.xml
COPY transaction-service/pom.xml transaction-service/pom.xml
COPY audit-service/pom.xml       audit-service/pom.xml
COPY blockchain-service/pom.xml  blockchain-service/pom.xml

# Download dependencies in a cacheable layer.
RUN mvn -B dependency:go-offline \
    -pl blockchain-service \
    -am \
    --no-transfer-progress || true

# Copy source only for what we need to compile
COPY shared/src           shared/src
COPY blockchain-service/src blockchain-service/src

# Build, skipping tests (tests should run in CI, not during image build).
RUN mvn -B package \
    -pl blockchain-service \
    -am \
    -DskipTests \
    --no-transfer-progress

# ---------------------------------------------------------------------------
# Stage 2 — Runtime
# ---------------------------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine AS runtime

# Non-root user for security hardening.
RUN addgroup -S bbss && adduser -S bbss -G bbss

WORKDIR /app

# Copy the fat JAR from the build stage.
COPY --from=build /workspace/blockchain-service/target/blockchain-service-*.jar app.jar

# Assign ownership.
RUN chown bbss:bbss app.jar

USER bbss

# Expose the service port.
EXPOSE 8084

# JVM tuning for containerised environments.
ENV JAVA_OPTS="-XX:+UseContainerSupport \
               -XX:MaxRAMPercentage=75.0 \
               -XX:InitialRAMPercentage=50.0 \
               -XX:+UseG1GC \
               --enable-preview \
               -Djava.security.egd=file:/dev/./urandom"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
