FROM openjdk:25-slim AS builder
WORKDIR /app
COPY . .
RUN ./gradlew :bot:bootJar

FROM openjdk:25-slim
WORKDIR /app
COPY --from=builder /app/bot/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
