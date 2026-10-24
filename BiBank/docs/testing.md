# Тестирование

## Как запустить

```bash
mvn verify                                   # все backend-тесты (Docker нужен для Testcontainers)
cd frontend && npm ci && npx ng test --watch=false
docker compose up --build -d && bash scripts/e2e-smoke.sh   # сквозной сценарий (curl, jq)
```

Интеграционные тесты помечены `@Testcontainers(disabledWithoutDocker = true)`: без Docker они пропускаются, а не падают.

## Backend

| Тест | Уровень | Что проверяет |
|---|---|---|
| `account-service` `LedgerServiceIntegrationTest` | PostgreSQL (Testcontainers) | двойная запись; идемпотентность `operationId`; отказ при нехватке средств, несбалансированных проводках, чужом счёте; 20 параллельных списаний при балансе на 10 — ровно 10 успешных, баланс 0; outbox-событие в той же транзакции |
| `auth-service` `AuthFlowIntegrationTest` | PostgreSQL + Redis (Testcontainers), MockMvc | регистрация (201, 409 дубликат), вход, доступ без токена (401), ротация refresh token и запрет повторного использования, logout, блокировка после 5 неверных паролей (429), блокировка администратором с отзывом сессий (403 на вход), запрет админ-API для USER, валидация полей |
| `auth-service` `JwtContractTest` | unit | токен Auth Service читается парсером из `common`; чужая подпись, подделка, истёкший токен отклоняются; refresh token непрозрачный |
| `payment-service` `TransferServiceTest` | Mockito | повтор с тем же ключом не трогает деньги; повтор PENDING-операции доводит её до конца; ключ с другими параметрами → 422; без ключа → 400; лимит; чужой счёт → 404; гонка двух запросов с одним ключом; точность конвертации |
| `loan-service` `CalculatorsTest` | unit | аннуитетный платёж; график гасит долг ровно до нуля; даты после коротких месяцев; нулевая ставка; ежедневные проценты; капитализация |
| `notification-service` `NotificationFactoryTest` | unit | тексты уведомлений, маскирование номеров, события без уведомления |
| `notification-service` `NotificationKafkaIntegrationTest` | Kafka + PostgreSQL (Testcontainers) | событие проходит Kafka → listener → БД; двойная доставка создаёт одно уведомление |

## Frontend (Vitest)

- `format.pipes.spec.ts` — деньги, курсы, номера счетов, склонения, заголовки и знаки операций.
- `auth.interceptor.spec.ts` — токен добавляется к запросам; при двух параллельных 401 выполняется один refresh и оба запроса повторяются; неудачный refresh завершает сессию.

## Сквозной сценарий `scripts/e2e-smoke.sh`

Работает с запущенной системой через gateway: регистрация двух клиентов, 401 без токена, 409 на дубликат,
открытие счетов, 404 на чужой счёт, идемпотентное пополнение (201 → 200), перевод и повтор, 422 сверх лимита,
FAILED при нехватке средств, конвертация RUB → USD, 20 параллельных переводов (баланс не уходит в минус,
часть запросов отсекает rate limit — 429), история, автоодобренный кредит с платежом, вклад с промоткой 30 дней,
403/200 для админ-API, здоровье системы, доставка уведомлений через Kafka, ротация и отзыв refresh token.

Адрес gateway передаётся первым аргументом: `bash scripts/e2e-smoke.sh http://localhost:8080` (это значение по умолчанию).
