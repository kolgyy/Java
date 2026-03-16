FROM eclipse-temurin:25-jdk-alpine

WORKDIR /app

COPY bot/target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java","-jar","/app/app.jar"]
