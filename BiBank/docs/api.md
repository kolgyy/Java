# REST API

Интерактивная документация: **http://localhost:4200/swagger-ui.html** (или `:8080/swagger-ui.html` напрямую через gateway).
Gateway собирает OpenAPI всех сервисов: `/api-docs/auth`, `/api-docs/account`, `/api-docs/payment`, `/api-docs/loan`, `/api-docs/notification`.
В Swagger UI нажмите **Authorize** и вставьте `accessToken` из ответа `/api/auth/login`.

## Общие правила

- Все запросы, кроме `/api/auth/**`, требуют `Authorization: Bearer <accessToken>`.
- Деньги передаются числами с двумя знаками после запятой, валюты — `RUB`, `USD`, `EUR`.
- Даты — ISO 8601 в UTC (`2026-10-23T20:50:00Z`), даты без времени — `YYYY-MM-DD`.
- Списки с пагинацией: параметры `page` (с нуля) и `size`, ответ `{ content, page, size, totalElements, totalPages }`.
- Денежные операции (`POST /api/transfers`, `POST /api/top-ups`) требуют заголовок `Idempotency-Key` (до 100 символов, обычно UUID).

## Формат ошибки

```json
{
  "timestamp": "2026-10-23T20:50:00Z",
  "status": 400,
  "error": "Bad Request",
  "code": "VALIDATION_FAILED",
  "message": "Некорректные входные данные",
  "path": "/api/auth/register",
  "fieldErrors": { "password": "Пароль должен содержать буквы и цифры" }
}
```

| HTTP | Когда | Примеры `code` |
|---|---|---|
| 400 | неверный запрос | `VALIDATION_FAILED`, `INVALID_AMOUNT`, `SAME_ACCOUNT`, `IDEMPOTENCY_KEY_REQUIRED` |
| 401 | нет токена, токен истёк, refresh недействителен | `UNAUTHORIZED` |
| 403 | нет роли, пользователь заблокирован | `FORBIDDEN`, `USER_BLOCKED` |
| 404 | нет объекта или он чужой | `NOT_FOUND` |
| 409 | конфликт | `USER_EXISTS` |
| 422 | бизнес-правило | `INSUFFICIENT_FUNDS`, `AMOUNT_LIMIT_EXCEEDED`, `ACCOUNT_NOT_ACTIVE`, `IDEMPOTENCY_KEY_REUSED`, `ACCOUNT_LIMIT` |
| 429 | перебор паролей (`TOO_MANY_ATTEMPTS`) или rate limit gateway (пустое тело, заголовки `X-RateLimit-*`) | `TOO_MANY_ATTEMPTS` |
| 503 | зависимый сервис недоступен | `SERVICE_UNAVAILABLE`, `DOWNSTREAM_ERROR` |

## Аутентификация

| Метод | Путь | Тело | Ответ |
|---|---|---|---|
| POST | `/api/auth/register` | `{ email, password, fullName }` | 201 `User` |
| POST | `/api/auth/login` | `{ email, password }` | 200 `{ accessToken, refreshToken, tokenType, expiresIn, user }` |
| POST | `/api/auth/refresh` | `{ refreshToken }` | 200 новая пара токенов, старый refresh больше не действует |
| POST | `/api/auth/logout` | `{ refreshToken }` | 204 |
| GET / PATCH | `/api/users/me` | `{ fullName }` | `User` |

Access token (HS256) содержит `sub` (email), `userId`, `role`, `name`, `type=access`, `iss=bibank-auth`, срок 15 минут.

## Счета

| Метод | Путь | Описание |
|---|---|---|
| GET | `/api/accounts` | счета пользователя |
| POST | `/api/accounts` | `{ currency, type: MAIN\|SAVINGS, name? }` → 201, не более 10 открытых счетов |
| GET | `/api/accounts/{id}` | счёт (только свой, иначе 404) |
| GET | `/api/accounts/{id}/balance` | `{ accountId, balance, currency }` |
| PATCH | `/api/accounts/{id}/status` | `{ status: ACTIVE\|FROZEN\|CLOSED }`, закрыть можно только пустой счёт |
| GET | `/api/accounts/lookup?number=` | получатель перевода: `{ number, currency, ownerName: "Борис П.", active }` |

Номер счёта — 20 цифр: `40817` + код валюты (`810`, `840`, `978`) + 12 случайных цифр.

## Переводы и операции

| Метод | Путь | Описание |
|---|---|---|
| POST | `/api/transfers/preview` | `{ fromAccountId, toAccountNumber, amount, description? }` → сумма зачисления, курс, получатель, хватает ли средств |
| POST | `/api/transfers` | то же тело + `Idempotency-Key`. 201 новая операция (`COMPLETED` или `FAILED`), 200 повтор, 202 `PENDING` |
| GET | `/api/transfers`, `/api/transfers/{id}` | переводы пользователя |
| POST | `/api/top-ups` | `{ accountId, amount }` + `Idempotency-Key`, демо-пополнение до 1 000 000 |
| GET | `/api/transactions` | все операции: `accountId`, `type`, `status`, `from`, `to` (даты, Москва), `page`, `size` |
| GET | `/api/transactions/{id}` | подробности операции |
| GET | `/api/exchange-rates` | `{ base: "RUB", rates: { RUB: 1, USD: 92.5, EUR: 100.2 } }` |

`Operation.direction` показывает операцию глазами пользователя: `OUT` — списание, `IN` — зачисление, `INTERNAL` — между своими счетами.
Лимит одного перевода — 10 000 000.

## Кредиты

| Метод | Путь | Описание |
|---|---|---|
| GET | `/api/loans/products` | каталог (кэш Redis) |
| POST | `/api/loans/calculate` | `{ productId, amount, termMonths }` → платёж, переплата, график |
| POST | `/api/loans/applications` | `{ productId, accountId, amount, termMonths, monthlyIncome?, purpose? }` → `SUBMITTED`, `APPROVED` или `REJECTED` |
| GET | `/api/loans/applications`, `/{id}` | заявки пользователя |
| POST | `/api/loans/applications/{id}/cancel` | отзыв заявки на рассмотрении |
| GET | `/api/loans`, `/api/loans/{id}` | кредиты, в карточке — график и платежи |
| POST | `/api/loans/{id}/payments` | `{ accountId, mode: NEXT\|FULL }` |

## Вклады

| Метод | Путь | Описание |
|---|---|---|
| GET | `/api/deposits/products` | каталог (кэш Redis) |
| GET | `/api/deposits` | вклады пользователя |
| POST | `/api/deposits` | `{ productId, accountId, amount, termMonths }` |
| GET | `/api/deposits/{id}` | вклад с последними 100 начислениями |
| POST | `/api/deposits/{id}/close` | закрытие (досрочно — по пониженной ставке) |

## Уведомления

`GET /api/notifications?unreadOnly&page&size`, `GET /api/notifications/unread-count`,
`POST /api/notifications/{id}/read`, `POST /api/notifications/read-all`.

## Администрирование (роль ADMIN)

| Метод | Путь | Описание |
|---|---|---|
| GET | `/api/admin/system/health` | состояние gateway и всех сервисов с задержкой ответа |
| GET | `/api/admin/users?search&status&page&size`, `/stats`, `/{id}` | пользователи |
| PATCH | `/api/admin/users/{id}/status` | `{ status: ACTIVE\|BLOCKED }`, блокировка отзывает все сессии |
| GET | `/api/admin/accounts?ownerId&status`, `/stats` | счета и деньги по валютам |
| GET | `/api/admin/transactions`, `/stats` | все операции, статистика по типам |
| GET | `/api/admin/loans/applications?status`, `/api/admin/loans/stats` | заявки, статистика кредитов и вкладов |
| POST | `/api/admin/loans/applications/{id}/approve` / `reject` | `{ reason? }` |
| PATCH | `/api/admin/loans/products/{id}`, `/api/admin/deposits/products/{id}` | `{ annualRate?, active? }` |
| POST | `/api/admin/deposits/simulate-days?days=30` | начислить проценты на N дней вперёд (1–400) |

## Пример: перевод через curl

```bash
TOKEN=$(curl -s -X POST localhost:8080/api/auth/login -H 'Content-Type: application/json' \
  -d '{"email":"admin@bibank.local","password":"Admin12345"}' | jq -r .accessToken)

curl -s -X POST localhost:8080/api/transfers \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -H "Idempotency-Key: $(uuidgen)" \
  -d '{"fromAccountId":"<uuid>","toAccountNumber":"40817810…","amount":1500.00,"description":"Обед"}'
```
