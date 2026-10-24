# BiBank

Учебная банковская система на микросервисах: регистрация и вход по JWT, счета в RUB/USD/EUR,
переводы с конвертацией, история операций, вклады с ежедневным начислением процентов,
кредиты с аннуитетным графиком, уведомления через Kafka и административная панель.
Клиентская часть написана на Angular.

> Все деньги в BiBank виртуальные. Система не подключается к настоящим банкам и не проводит реальных платежей.

![Стек](https://img.shields.io/badge/Java-21-orange) ![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5-green)
![Angular](https://img.shields.io/badge/Angular-21-red) ![Kafka](https://img.shields.io/badge/Kafka-3.9-black)

## Быстрый старт

Нужен только Docker (Docker Desktop или Docker Engine с Compose v2). Java, Maven и Node.js устанавливать не обязательно:
всё собирается внутри контейнеров.

```bash
git clone <repository-url> bibank
cd bibank
docker compose up --build
```

Первая сборка занимает 5–10 минут (скачиваются зависимости Maven и npm), последующие быстрее.
Когда все контейнеры станут `healthy`, откройте:

| Что | Адрес |
|---|---|
| Веб-приложение | http://localhost:4200 |
| Swagger UI (все сервисы) | http://localhost:4200/swagger-ui.html |
| API Gateway напрямую | http://localhost:8080 |
| Kafka UI (по желанию) | `docker compose --profile tools up -d kafka-ui` → http://localhost:8090 |

Администратор создаётся при первом запуске: **admin@bibank.local / Admin12345**.
Обычного клиента можно зарегистрировать прямо на странице входа.

Файл `.env` не обязателен: без него используются значения по умолчанию, пригодные только для локальной машины.
Чтобы задать свои секреты и порты, скопируйте пример и отредактируйте его:

```bash
cp .env.example .env
```

Остановить систему: `docker compose down`. Удалить ещё и данные (БД, Kafka, Redis): `docker compose down -v`.
Пароли пользователей БД применяются только при первом создании тома PostgreSQL: если поменяли их в `.env` после запуска, выполните `docker compose down -v`.

### Что попробовать

1. Зарегистрироваться, открыть счета в рублях и долларах.
2. Пополнить рублёвый счёт (демо-пополнение) и перевести часть денег на долларовый: перевод покажет курс и сумму зачисления.
3. Зарегистрировать второго пользователя и перевести ему деньги по номеру счёта: получатель определится автоматически, у обоих появятся уведомления.
4. Открыть вклад, затем войти администратором и на вкладке «Продукты и ставки» «промотать» 30 дней: в карточке вклада появятся ежедневные начисления.
5. Взять кредит до 300 000 ₽: он одобряется автоматически, деньги приходят на счёт, в карточке кредита виден график, можно внести платёж.
   Заявку на большую сумму администратор одобряет или отклоняет вручную.
6. В админке заблокировать пользователя: его сессии сразу завершаются, а gateway перестаёт пропускать его запросы.

## Архитектура

```
                    ┌────────────────────────┐
  Браузер ────────▶ │ frontend (nginx+Angular)│  :4200
                    └───────────┬────────────┘
                                │ /api, /swagger-ui
                    ┌───────────▼────────────┐       ┌───────┐
                    │      api-gateway       │──────▶│ Redis │ rate limit, блокировки,
                    │ JWT, CORS, rate limit  │       └───────┘ refresh-сессии, кэш
                    └───┬────┬────┬────┬────┬┘
          ┌─────────────┘    │    │    │    └──────────────┐
   ┌──────▼─────┐ ┌──────────▼┐ ┌─▼──────────┐ ┌──────────▼──┐ ┌──────────────┐
   │auth-service│ │ account-  │ │ payment-   │ │ loan-service│ │notification- │
   │ users, JWT │ │ service   │◀┤ service    │◀┤ кредиты,    │ │ service      │
   │ сессии     │ │ счета,    │ │ переводы,  │ │ вклады      │ │ уведомления  │
   └─────┬──────┘ │ проводки  │ │ история    │ └──────┬──────┘ └──────▲───────┘
         │        └─────┬─────┘ └─────┬──────┘        │               │
         │   outbox     │   outbox    │   outbox      │   outbox      │
         └──────────────┴──────┬──────┴───────────────┘               │
                               ▼                                      │
                         ┌───────────┐  user/account/payment/loan     │
                         │   Kafka   │────────────────────────────────┘
                         └───────────┘
   PostgreSQL: отдельная база и отдельный пользователь у каждого сервиса.
```

Ключевые решения (подробно в [docs/architecture.md](docs/architecture.md)):

- **Балансы меняет только Account Service.** Каждая операция — набор проводок, сумма которых по каждой валюте равна нулю
  (двойная запись). Проводки применяются в одной транзакции PostgreSQL под пессимистичными блокировками счетов,
  которые берутся в порядке id, поэтому встречные переводы не дают дедлоков. CHECK-ограничение в БД дополнительно
  запрещает отрицательный баланс клиентского счёта.
- **Идемпотентность на двух уровнях.** Клиент передаёт заголовок `Idempotency-Key` (уникален для пары пользователь + ключ),
  а Payment Service передаёт в Account Service `operationId`. Повторная отправка возвращает ту же операцию, деньги не списываются дважды.
- **Transactional Outbox.** Бизнес-изменение и событие сохраняются в одной транзакции, отдельный планировщик публикует
  события в Kafka (`FOR UPDATE SKIP LOCKED`, поэтому несколько экземпляров сервиса не мешают друг другу).
- **Сага при выдаче кредита.** Заявка → кредит `PENDING_DISBURSEMENT` → зачисление через Payment Service → `ACTIVE`.
  При отказе зачисления кредит отменяется, заявка переходит в `REJECTED`. Незавершённые шаги повторяются фоновым заданием.
- **Устойчивость к сбоям соседей.** Если Account Service недоступен, операция остаётся `PENDING` (HTTP 202)
  и завершается фоновым реконсилятором.
- **Notification Service** обрабатывает события at-least-once и не создаёт дубликатов
  (`INSERT … ON CONFLICT (event_id, user_id) DO NOTHING`). Сбойные сообщения после трёх попыток уходят в топик `*.dlt`.
- **Проверка прав в каждом сервисе.** Gateway проверяет JWT и блокировку пользователя, но каждый сервис ещё раз проверяет токен
  и принадлежность данных. Внутренние endpoint'ы `/internal/**` закрыты на gateway и требуют секретный заголовок `X-Internal-Token`.

## Сервисы и порты

| Сервис | Порт | База | Ответственность |
|---|---|---|---|
| frontend | 4200 → 80 | — | Angular SPA, nginx, проксирование `/api` на gateway |
| api-gateway | 8080 | — | Маршрутизация, JWT, CORS, rate limit (Redis), блокировка пользователей, сводный Swagger, health системы |
| auth-service | 8081 | bibank_auth | Пользователи, BCrypt, JWT access, одноразовые refresh token в Redis, защита от перебора паролей |
| account-service | 8082 | bibank_accounts | Счета, номера, статусы, журнал проводок, системные счета банка |
| payment-service | 8083 | bibank_payments | Переводы, пополнения, история, курсы валют (кэш в Redis), внутренние операции для кредитов и вкладов |
| loan-service | 8084 | bibank_loans | Кредитные и депозитные продукты (кэш в Redis), заявки, график, погашения, начисление процентов |
| notification-service | 8085 | bibank_notifications | Потребитель Kafka, уведомления пользователя |
| postgres | 5432 (хост) | — | Одна СУБД, отдельная БД и пользователь на сервис (`infrastructure/docker/postgres/init-databases.sh`) |
| redis | — | — | Сессии, rate limit, кэш |
| kafka | 29092 (хост) | — | KRaft, без ZooKeeper |

Сервисы не публикуют порты на хост: вся внешняя работа идёт через gateway.

## Технологии

**Backend:** Java 21, Spring Boot 3.5, Spring Cloud Gateway (WebFlux), Spring Security, Spring Data JPA / Hibernate,
Spring Kafka, Spring Data Redis, Liquibase, PostgreSQL 16, Kafka 3.9 (KRaft), Redis 7.4, jjwt, springdoc-openapi, Lombok.

**Frontend:** Angular 21 (standalone-компоненты, signals, zoneless, lazy routes, функциональные guards и interceptor), SCSS без UI-библиотек, Vitest.

**Инфраструктура и тесты:** Docker, Docker Compose, nginx, Kubernetes-манифесты, JUnit 5, Mockito, AssertJ, Testcontainers, MockMvc.

## Структура репозитория

```
bibank/
├── frontend/                Angular-приложение, Dockerfile, nginx.conf
├── api-gateway/             Spring Cloud Gateway
├── auth-service/            аутентификация и пользователи
├── account-service/         счета и проводки
├── payment-service/         переводы и история операций
├── loan-service/            кредиты и вклады
├── notification-service/    уведомления
├── common/                  общий код: JWT, ошибки, outbox, события, межсервисный клиент
├── infrastructure/
│   ├── docker/              общий Dockerfile бэкенда, инициализация PostgreSQL
│   └── kubernetes/          манифесты для кластера
├── docs/                    архитектура, API, события Kafka, тестирование
├── scripts/e2e-smoke.sh     сквозной сценарий через gateway (curl + jq)
├── docker-compose.yml
└── .env.example
```

Внутри каждого Java-сервиса: `config`, `controller`, `dto`, `entity`, `repository`, `service`, ресурсы с миграциями
в `src/main/resources/db/changelog`.

## REST API

Полный список с моделями — в Swagger UI и в [docs/api.md](docs/api.md). Основные группы:

| Группа | Endpoint'ы |
|---|---|
| Аутентификация | `POST /api/auth/register`, `/login`, `/refresh`, `/logout` |
| Профиль | `GET/PATCH /api/users/me` |
| Счета | `GET/POST /api/accounts`, `GET /api/accounts/{id}`, `/{id}/balance`, `PATCH /{id}/status`, `GET /api/accounts/lookup?number=` |
| Переводы | `POST /api/transfers/preview`, `POST /api/transfers` (заголовок `Idempotency-Key`), `GET /api/transfers`, `/{id}`, `POST /api/top-ups` |
| История | `GET /api/transactions?accountId&type&status&from&to&page&size`, `GET /api/transactions/{id}`, `GET /api/exchange-rates` |
| Кредиты | `GET /api/loans/products`, `POST /api/loans/calculate`, `POST/GET /api/loans/applications`, `POST /api/loans/applications/{id}/cancel`, `GET /api/loans`, `/{id}`, `POST /api/loans/{id}/payments` |
| Вклады | `GET /api/deposits/products`, `GET/POST /api/deposits`, `GET /api/deposits/{id}`, `POST /api/deposits/{id}/close` |
| Уведомления | `GET /api/notifications`, `/unread-count`, `POST /{id}/read`, `POST /read-all` |
| Администрирование | `/api/admin/users` (+ `/stats`, `PATCH /{id}/status`), `/api/admin/accounts`, `/api/admin/transactions`, `/api/admin/loans/applications/{id}/approve|reject`, `PATCH /api/admin/loans/products/{id}`, `PATCH /api/admin/deposits/products/{id}`, `POST /api/admin/deposits/simulate-days`, `GET /api/admin/system/health` |

Все ошибки возвращаются в одном формате:

```json
{ "timestamp": "…", "status": 422, "error": "Unprocessable Entity", "code": "INSUFFICIENT_FUNDS",
  "message": "Недостаточно средств на счёте", "path": "/api/transfers", "fieldErrors": null }
```

## Тесты

```bash
mvn verify                 # backend: unit + интеграционные (нужен Docker для Testcontainers)
cd frontend && npm ci && npx ng test --watch=false   # frontend
bash scripts/e2e-smoke.sh  # сквозной сценарий по запущенной системе (нужны curl и jq)
```

Что проверяется (подробно в [docs/testing.md](docs/testing.md)):

- **Ledger на настоящем PostgreSQL:** 20 одновременных списаний с одного счёта — успешно ровно столько, сколько позволяет баланс;
  повтор `operationId` не меняет балансы; несбалансированные проводки, чужой счёт и нехватка средств отклоняются; событие outbox пишется в той же транзакции.
- **Auth на PostgreSQL и Redis:** регистрация, вход, ротация refresh token (повторное использование → 401), выход, блокировка после 5 неверных паролей, блокировка пользователя администратором с отзывом сессий, валидация.
- **Контракт JWT** между Auth Service и остальными сервисами: подпись, подделка, истечение срока.
- **Переводы (Mockito):** повтор с тем же ключом, повтор с другими параметрами (422), гонка двух запросов с одним ключом, лимиты, чужой счёт, конвертация.
- **Кредиты и вклады:** аннуитет, график до нуля, даты в коротких месяцах, ежедневные проценты и капитализация.
- **Kafka → уведомления на Testcontainers Kafka:** двойная доставка события создаёт одно уведомление.
- **Frontend:** форматирование денег и курсов, маски номеров, единичный refresh при параллельных 401 и выход при неудачном refresh.
- **E2E-скрипт:** полный путь пользователя и администратора через gateway, включая 401/403/404/409/422/429.

## Локальная разработка без Docker для сервисов

```bash
docker compose up -d postgres redis kafka        # только инфраструктура
mvn install -DskipTests                           # один раз, чтобы собрать модуль common
mvn -pl auth-service spring-boot:run              # и так далее для нужных сервисов
cd frontend && npm ci && npx ng serve             # http://localhost:4200, /api проксируется на :8080
```

Для запуска сервисов вне Docker задайте переменные окружения из `.env.example`
(`POSTGRES_HOST=localhost`, `KAFKA_BOOTSTRAP_SERVERS=localhost:29092`, `JWT_SECRET`, `INTERNAL_API_TOKEN` и пароли БД).

## Безопасность

- Пароли хешируются BCrypt (cost 12), в ответах API их нет.
- Access token живёт 15 минут, refresh token — непрозрачная случайная строка, в Redis хранится только её SHA-256, используется один раз.
- Пять неверных паролей за 15 минут блокируют вход по этому email; gateway ограничивает частоту запросов к `/api/auth/**` и к денежным операциям.
- Gateway удаляет из входящих запросов служебные заголовки `X-User-*` и `X-Internal-Token`, добавляет `X-Request-Id`.
- Каждый сервис сам проверяет JWT и владельца данных; чужой счёт неотличим от несуществующего (404).
- Секреты не хранятся в репозитории: они передаются через переменные окружения (`.env` в `.gitignore`), в Kubernetes — через Secret.
- Пароли и токены не логируются.

## Kubernetes

В `infrastructure/kubernetes` лежат манифесты: Namespace, ConfigMap, Secret (пример), Deployment и Service для каждого
сервиса с readiness/liveness probes и лимитами ресурсов, StatefulSet для PostgreSQL, Redis и Kafka, Ingress.
Инструкция — в [infrastructure/kubernetes/README.md](infrastructure/kubernetes/README.md).

## Текущий статус

| Компонент | Статус |
|---|---|
| API Gateway | Готов: маршруты, JWT, CORS, rate limit, блокировки, сводный Swagger, health системы |
| Auth Service | Готов: регистрация, вход, refresh/logout, роли, админ-API, защита от перебора |
| Account Service | Готов: счета RUB/USD/EUR, статусы, двойная запись, блокировки, идемпотентность |
| Payment Service | Готов: переводы, конвертация, пополнения, история с фильтрами, реконсиляция PENDING |
| Loan Service | Готов: кредиты (сага выдачи, график, погашение), вклады (начисления, капитализация, досрочное закрытие) |
| Notification Service | Готов: Kafka, дедупликация, DLT, API уведомлений |
| Kafka | Готова: 4 топика + DLT, Transactional Outbox во всех сервисах-производителях |
| Redis | Готов: refresh-сессии, блокировки, rate limit, кэш курсов и продуктов |
| Frontend | Готов: Angular, 14 страниц, адаптивная вёрстка |
| Docker Compose | Готов: вся система поднимается одной командой |
| Тесты | 32 backend-теста (в том числе Testcontainers), 12 frontend-тестов, сквозной скрипт |
| Kubernetes | Манифесты подготовлены, в реальном кластере не проверялись |

Возможные направления развития: email/Telegram-канал уведомлений, внешний источник курсов валют,
метрики Prometheus и трассировка, нагрузочное тестирование (k6/Gatling).

## Лицензия

Лицензия пока не выбрана. Перед публикацией репозитория добавьте файл `LICENSE`.
