FROM maven:3.9.6-eclipse-temurin-21-alpine AS build

WORKDIR /workspace
ENV MAVEN_OPTS="-Dmaven.wagon.http.retryHandler.count=5 -Dmaven.wagon.rto=600000 -Dmaven.wagon.http.pool=false -Daether.connector.basic.threads=1"

COPY pom.xml ./pom.xml
# All module POM stubs — required for Maven multi-module reactor validation
COPY shared/pom.xml              shared/pom.xml
COPY api-gateway/pom.xml         api-gateway/pom.xml
COPY tenant-service/pom.xml      tenant-service/pom.xml
COPY transaction-service/pom.xml transaction-service/pom.xml
COPY blockchain-service/pom.xml  blockchain-service/pom.xml
COPY audit-service/pom.xml       audit-service/pom.xml
COPY auth-service/pom.xml        auth-service/pom.xml

COPY shared/src shared/src
COPY auth-service/src auth-service/src
RUN --mount=type=cache,id=bbss-m2,target=/root/.m2 \
    mvn -B package -pl auth-service -am -DskipTests --no-transfer-progress

FROM eclipse-temurin:21-jre-alpine

RUN addgroup -S bbss && adduser -S bbss -G bbss

WORKDIR /app

COPY --from=build /workspace/auth-service/target/auth-service-*.jar app.jar

RUN chown bbss:bbss app.jar

USER bbss

EXPOSE 8081

ENV JAVA_OPTS="-XX:+UseContainerSupport \
               -XX:MaxRAMPercentage=75.0 \
               -XX:+UseG1GC \
               --enable-preview \
               -Djava.security.egd=file:/dev/./urandom"

HEALTHCHECK --interval=30s --timeout=10s --start-period=90s --retries=3 \
    CMD wget -qO- http://localhost:8081/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
