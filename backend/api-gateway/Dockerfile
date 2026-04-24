FROM maven:3.9.6-eclipse-temurin-21-alpine AS build
WORKDIR /app
ENV MAVEN_OPTS="-Dmaven.wagon.http.retryHandler.count=5 -Dmaven.wagon.rto=600000 -Dmaven.wagon.http.pool=false -Daether.connector.basic.threads=1"

# Parent POM
COPY pom.xml ./pom.xml

# All module POM stubs — required for Maven multi-module reactor validation
COPY shared/pom.xml              ./shared/pom.xml
COPY auth-service/pom.xml        ./auth-service/pom.xml
COPY tenant-service/pom.xml      ./tenant-service/pom.xml
COPY transaction-service/pom.xml ./transaction-service/pom.xml
COPY blockchain-service/pom.xml  ./blockchain-service/pom.xml
COPY audit-service/pom.xml       ./audit-service/pom.xml
COPY api-gateway/pom.xml         ./api-gateway/pom.xml

COPY shared/src      ./shared/src
COPY api-gateway/src ./api-gateway/src

RUN --mount=type=cache,id=bbss-m2,target=/root/.m2 \
    mvn package -pl api-gateway -am -DskipTests -q

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/api-gateway/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "--enable-preview", "-jar", "app.jar"]
