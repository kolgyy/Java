# syntax=docker/dockerfile:1.7
# Единый Dockerfile для Java-сервисов. Стадия сборки одинакова для всех сервисов,
# поэтому BuildKit выполняет её один раз; итоговый образ содержит только jar нужного сервиса.

# MAVEN_IMAGE можно переопределить, например образом с настройками корпоративного прокси
ARG MAVEN_IMAGE=maven:3.9-eclipse-temurin-21
ARG RUNTIME_IMAGE=eclipse-temurin:21-jre-alpine

FROM ${MAVEN_IMAGE} AS build
WORKDIR /build

# Сначала только pom-файлы — слой с зависимостями кэшируется, пока не меняются pom.xml
COPY pom.xml .
COPY common/pom.xml common/
COPY api-gateway/pom.xml api-gateway/
COPY auth-service/pom.xml auth-service/
COPY account-service/pom.xml account-service/
COPY payment-service/pom.xml payment-service/
COPY loan-service/pom.xml loan-service/
COPY notification-service/pom.xml notification-service/
RUN --mount=type=cache,target=/root/.m2,sharing=locked \
    mvn -B -q -pl common dependency:go-offline -DexcludeGroupIds=ru.bibank || true

COPY common/src common/src
COPY api-gateway/src api-gateway/src
COPY auth-service/src auth-service/src
COPY account-service/src account-service/src
COPY payment-service/src payment-service/src
COPY loan-service/src loan-service/src
COPY notification-service/src notification-service/src
RUN --mount=type=cache,target=/root/.m2,sharing=locked \
    mvn -B -q package -DskipTests

FROM ${RUNTIME_IMAGE}
ARG SERVICE
RUN addgroup -S bibank && adduser -S bibank -G bibank
WORKDIR /app
COPY --from=build /build/${SERVICE}/target/${SERVICE}.jar app.jar
USER bibank
ENV LIQUIBASE_ANALYTICS_ENABLED=false
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+UseG1GC -Djava.security.egd=file:/dev/./urandom"
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
