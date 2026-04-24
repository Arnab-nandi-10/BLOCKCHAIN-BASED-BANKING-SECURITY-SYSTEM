# =============================================================================
# Audit Service – Multi-stage Docker build
# =============================================================================

FROM maven:3.9.6-eclipse-temurin-21-alpine AS deps

WORKDIR /workspace
ENV MAVEN_OPTS="-Dmaven.wagon.http.retryHandler.count=5 -Dmaven.wagon.rto=600000 -Dmaven.wagon.http.pool=false -Daether.connector.basic.threads=1"

# Parent POM
COPY pom.xml ./pom.xml

# All module POM stubs — required for Maven multi-module reactor validation
COPY shared/pom.xml              shared/pom.xml
COPY api-gateway/pom.xml         api-gateway/pom.xml
COPY auth-service/pom.xml        auth-service/pom.xml
COPY tenant-service/pom.xml      tenant-service/pom.xml
COPY transaction-service/pom.xml transaction-service/pom.xml
COPY blockchain-service/pom.xml  blockchain-service/pom.xml
COPY audit-service/pom.xml       audit-service/pom.xml

FROM deps AS builder

WORKDIR /workspace

COPY shared/src      shared/src
COPY audit-service/src audit-service/src

RUN --mount=type=cache,id=bbss-m2,target=/root/.m2 \
    mvn install \
        -pl shared,audit-service \
        -am \
        -DskipTests \
        --no-transfer-progress \
        -q

FROM eclipse-temurin:21-jre-alpine AS runner

RUN addgroup --system --gid 1001 appgroup \
 && adduser  --system --uid 1001 --ingroup appgroup --no-create-home appuser

WORKDIR /app

COPY --from=builder --chown=appuser:appgroup \
     /workspace/audit-service/target/audit-service-*.jar \
     app.jar

USER appuser

EXPOSE 8085

ENV JAVA_OPTS="\
  -XX:+UseContainerSupport \
  -XX:MaxRAMPercentage=75.0 \
  -XX:TieredStopAtLevel=1 \
  --enable-preview \
  -Djava.security.egd=file:/dev/./urandom \
  -Dspring.backgroundpreinitializer.ignore=true"

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]

LABEL org.opencontainers.image.title="Civic Savings Audit Service" \
      org.opencontainers.image.description="Blockchain-anchored immutable audit trail service" \
      org.opencontainers.image.version="1.0.0"
