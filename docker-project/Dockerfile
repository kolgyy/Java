# Используем образ Maven с Java 17 из Dockerhub, обзываем этот образ просто build
FROM maven:3.8.6-amazoncorretto-17 AS build
# Копируем файл pom.xml в папку build - рабочую папку
COPY pom.xml /build/
# Назначаем workdir
WORKDIR /build/
# Запускаем команду Maven, которая выкачивает все зависимости, это необязательно, но полезно
RUN mvn dependency:go-offline
# Копируем папку src в build
COPY src /build/src/
# Теперь в папке build лежит pom.xml и папка src с файлами, осталось запустить сборку проекта
RUN mvn package -DskipTests
# Тесты пропустили, переходим ко второму этапу сборки.
# Зачем делать сборку в два этапа? Благодаря двум этапам мы сначала собираем образ, а затем лишь запускаем контейнер.
# Run stage
# В образе Maven не нужен, нужна только Java, alpine означает, что версия урезанная, быстрая и маленькая
FROM eclipse-temurin:17-jre-alpine
# С помощью ARG мы создаём переменные, создадим такую
ARG JAR_FILE=/build/target/*.jar
# Присвоили переменной .jar-файл, который является нашим проектом
# Теперь копируем этот файл в папку, в которой будем запускать приложение
COPY --from=build $JAR_FILE /opt/docker-project/app.jar
# opt - это корневая папка в Linux, мы перенесли туда джарник и назвали его app.jar (название может быть любое)
# Осталось запустить джарник с помощью команды Entrypoint
ENTRYPOINT ["java", "-jar", "/opt/docker-project/app.jar"]
