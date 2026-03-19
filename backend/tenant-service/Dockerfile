FROM maven:3.9-eclipse-temurin-21-alpine AS build
WORKDIR /app

# Parent POM
COPY pom.xml ./pom.xml

# All module POM stubs — required for Maven multi-module reactor validation
COPY shared/pom.xml              ./shared/pom.xml
COPY api-gateway/pom.xml         ./api-gateway/pom.xml
COPY auth-service/pom.xml        ./auth-service/pom.xml
COPY transaction-service/pom.xml ./transaction-service/pom.xml
COPY blockchain-service/pom.xml  ./blockchain-service/pom.xml
COPY audit-service/pom.xml       ./audit-service/pom.xml
COPY tenant-service/pom.xml      ./tenant-service/pom.xml

RUN mvn dependency:go-offline -pl tenant-service -am -q || true

COPY shared/src       ./shared/src
COPY tenant-service/src ./tenant-service/src

RUN mvn package -pl tenant-service -am -DskipTests -q

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/tenant-service/target/*.jar app.jar
EXPOSE 8082
ENTRYPOINT ["java", "--enable-preview", "-jar", "app.jar"]
