# LinkTracker

LinkTracker – Telegram-бот, который отслеживает изменения на веб-страницах и оперативно информирует пользователя о них.

## Project Structure

- **bot/** - Telegram-бот (взаимодействие с пользователем через Telegram Bot API)
- **scrapper/** - Сервис мониторинга ссылок
- **ai-agent/** - Интеллектуальная обработка данных

## Configuration

В корне проекта нужно создать .env файл и указать параметры из **.env.example** (либо же использовать запуск с использованием переменных среды):

- **SPRING_PROFILES_ACTIVE=dev** - профиль запуска приложения.
  Доступные параметры: **dev** и **prod**.
- **TELEGRAM_TOKEN=your_real_telegram_token** - токен бота Телеграм, его можно получить, используя BotFather в Телеграм.
- **GITHUB_TOKEN=your_real_github_token** - токен для Github API.
- **STACKOVERFLOW_ACCESS_KEY**
- **STACKOVERFLOW_KEY**
- **COMMUNICATION_PROTOCOL=http**
  Доступные параметры: **http** и **grpc**.

## Running the Bot

Запуск из корня проекта:

```bash
SPRING_PROFILES_ACTIVE=dev TELEGRAM_TOKEN=your_real_token GITHUB_TOKEN=your_real_token STACKOVERFLOW_ACCESS_KEY=your_real_token STACKOVERFLOW_KEY=your_real_token COMMUNICATION PROTOCOL=http/grpc ./mvnw spring-boot:run -pl bot

**Или запустите основной класс с @SpringBootApplication через IDE, указав .env-файл с переменными токена и профиля.**

## Project Build

**Linux / MacOS**
./mvnw clean verify

**Windows**:
mvnw.cmd clean verify

## Run tests

./mvnw test

## Requirements

- **JDK 25+**
- Maven 3.9.12 (or use mvnw)
- Telegram Bot Token

## Code Quality

- **Format code:**
  ./mvnw spotless:apply
- **Check linters:**
  mvn clean compile -am spotless:check modernizer:modernizer spotbugs:check pmd:check pmd:cpd-check

```

## Running the Scrapper

Запуск из корня проекта:

```bash
SPRING_PROFILES_ACTIVE=dev TELEGRAM_TOKEN=your_real_token GITHUB_TOKEN=your_real_token STACKOVERFLOW_ACCESS_KEY=your_real_token STACKOVERFLOW_KEY=your_real_token COMMUNICATION PROTOCOL=http/grpc ./mvnw spring-boot:run -pl scrapper

**Или запустите основной класс с @SpringBootApplication через IDE, указав .env-файл с переменными токена и профиля.**

## Project Build

**Linux / MacOS**
./mvnw clean verify

**Windows**:
mvnw.cmd clean verify

## Run tests

./mvnw test

## Requirements

- **JDK 25+**
- Maven 3.9.12 (or use mvnw)
- Telegram Bot Token

## Code Quality

- **Format code:**
  ./mvnw spotless:apply
- **Check linters:**
  mvn clean compile -am spotless:check modernizer:modernizer spotbugs:check pmd:check pmd:cpd-check

```

