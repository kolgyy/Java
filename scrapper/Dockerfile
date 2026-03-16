FROM eclipse-temurin:25-jre-jammy

WORKDIR /app

COPY scrapper/target/*.jar app.jar

EXPOSE 8081

ENTRYPOINT ["java","-jar","/app/app.jar"]
