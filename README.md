# Java Projects Collection

Этот репозиторий содержит набор Java-приложений, демонстрирующих разработку
backend-систем, REST API, веб-приложений и микросервисов с использованием
Spring, Hibernate, Kafka, Docker и сопутствующих технологий.

Каждый проект находится в отдельной папке и является самостоятельным приложением
с собственной архитектурой и назначением.

## Флагманский проект

### [BiBank](BiBank) — учебная банковская система на микросервисах

Полноценный pet-проект: 6 Spring Boot сервисов, API Gateway, Kafka, Redis, PostgreSQL и Angular-фронтенд,
запускается одной командой `docker compose up --build`.

- Счета в RUB/USD/EUR, переводы с конвертацией, история операций, вклады с ежедневным начислением процентов, кредиты с аннуитетным графиком, уведомления, админ-панель
- Двойная запись (double-entry ledger), пессимистичные блокировки, идемпотентность по `Idempotency-Key`, Transactional Outbox, сага выдачи кредита
- JWT + одноразовые refresh token в Redis, rate limit, проверка прав в каждом сервисе
- Тесты на Testcontainers (PostgreSQL, Redis, Kafka), сквозной сценарий, манифесты Kubernetes

Подробности — в [BiBank/README.md](BiBank/README.md).

## Projects Overview

- **DigitalLibraryBoot** — REST-приложение библиотеки на Spring Boot
- **DigitalLibraryProject1** — базовая версия библиотечной системы (MVC)
- **DigitalLibraryProject2** — расширенная версия библиотеки с улучшенной архитектурой
- **GradleProject** — пример Java-проекта с Gradle
- **HibernateApp** — работа с БД через Hibernate ORM
- **ProductNotifier** — микросервисная система уведомлений о продуктах
- **SensorAppRESTApiProject3** — REST API для обработки данных сенсоров
- **SpringMVCWebApp** — веб-приложение на Spring MVC
- **SpringMVCWebAppHibernate** — Spring MVC + Hibernate
- **SpringSecurityApp** — аутентификация и авторизация с Spring Security
- **SpringWebApp** — базовое веб-приложение на Spring
- **TransferAPI_KafkaTransactions** — транзакции и обмен сообщениями через Kafka
- **docker-project** — Spring Boot приложение, запускаемое в Docker
- **KubernetesProject** — проект с манифестами для запуска в Kubernetes
- **ExpenseTracker** — графическое приложение учёта финансовых расходов отделов в компании
- **SpringCloudDemo** — Spring Cloud: Eureka Server, клиенты сервиса и API Gateway
- **BiBank** — микросервисная банковская система с Angular-фронтендом (см. выше)

## Technologies Used

- Java
- Spring Boot / Spring MVC / Spring Security
- Hibernate / JPA
- PostgreSQL
- JDBC
- H2
- REST API
- Kafka
- Maven / Gradle
- Docker / Docker Compose
- Redis
- Liquibase
- Spring Cloud Gateway
- Testcontainers / JUnit 5 / Mockito
- Kubernetes
- Angular

Каждый проект содержит собственный `README.md` с подробным описанием и инструкциями
по запуску.
