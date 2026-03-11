FROM openjdk:25-slim AS builder
WORKDIR /app
COPY . .
RUN ./gradlew :scrapper:bootJar

FROM openjdk:25-slim
WORKDIR /app
COPY --from=builder /app/scrapper/build/libs/*.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
