# Архитектура BiBank

## Компоненты

| Компонент | Тип | Данные |
|---|---|---|
| frontend | Angular SPA за nginx | — |
| api-gateway | Spring Cloud Gateway (WebFlux) | Redis: rate limit, флаги блокировки |
| auth-service | Spring MVC | PostgreSQL `bibank_auth`, Redis: refresh-сессии, счётчики неудачных входов |
| account-service | Spring MVC | PostgreSQL `bibank_accounts` |
| payment-service | Spring MVC | PostgreSQL `bibank_payments`, Redis: кэш курсов |
| loan-service | Spring MVC | PostgreSQL `bibank_loans`, Redis: кэш каталога продуктов |
| notification-service | Spring MVC + Kafka consumer | PostgreSQL `bibank_notifications` |

Каждый сервис владеет своей базой и подключается к ней своим пользователем: у сервиса нет прав на чужие таблицы.
Схемы создаёт Liquibase при старте сервиса (`src/main/resources/db/changelog`).
Таблица `outbox_events` подключается к каждому производителю событий из модуля `common`.

## Безопасность запроса

```
Браузер ──Bearer JWT──▶ gateway
   1. проверка подписи и срока JWT (HS256, общий секрет JWT_SECRET)
   2. blocked:{userId} в Redis → 403, если пользователь заблокирован
   3. rate limit (Redis): /api/auth/** по IP, POST /api/transfers и /api/top-ups по пользователю
   4. удаление X-User-*, X-Internal-Token из запроса, добавление X-Request-Id
   5. /internal/** → 403
        │
        ▼
   сервис: повторная проверка JWT (JwtAuthenticationFilter из common),
           роль ADMIN для /api/admin/**, проверка владельца данных в бизнес-логике
```

Межсервисные вызовы идут напрямую (не через gateway) на `/internal/**` с заголовком `X-Internal-Token`
(`InternalTokenFilter`). `InternalRestClient` переводит 4xx соседа в `ApiException` с тем же кодом,
а сетевые ошибки и 5xx — в `DownstreamUnavailableException` (503).

## Деньги: ledger в Account Service

Баланс меняется только методом `LedgerService.apply(OperationRequest)`:

1. Запрос — `operationId` и список проводок `(accountId, amount, expectedOwnerId?)`.
2. Счета блокируются `SELECT … FOR UPDATE` в порядке возрастания id: две встречные операции не могут захватить
   блокировки в разном порядке, дедлока нет.
3. Если `operationId` уже есть в `applied_operations`, возвращается сохранённый результат (идемпотентность).
   Проверка идёт после захвата блокировок, поэтому параллельный дубликат ждёт первую транзакцию и видит её результат.
4. Проверки: счёт активен, принадлежит ожидаемому владельцу, у клиентского счёта хватает средств,
   сумма проводок по каждой валюте равна нулю.
5. Пишутся `applied_operations` и `ledger_entries` (с остатком после проводки), обновляются балансы. Всё в одной транзакции.

Деньги «извне» (пополнение, выдача кредита, проценты) приходят с системных счетов банка `SYSTEM` (по одному на валюту).
Им разрешён отрицательный баланс: это зеркало обязательств банка, и сумма всех балансов в каждой валюте всегда равна нулю.

Конвертация валют — четыре проводки: клиент RUB → системный RUB, системный USD → клиент USD.
Каждая валюта сбалансирована отдельно.

## Операции в Payment Service

```
POST /api/transfers (Idempotency-Key)
  ├─ ключ уже есть → те же параметры? вернуть операцию (200) : 422 IDEMPOTENCY_KEY_REUSED
  ├─ проверки суммы, лимита, счёта списания (свой, активный), получателя
  ├─ INSERT operation(PENDING)  ← UNIQUE(initiator_id, idempotency_key) ловит гонку двух запросов
  └─ OperationExecutor:
        POST account-service /internal/operations (operationId = id операции)
          ├─ успех       → COMPLETED + событие TransferCompleted/TransferReceived (outbox, та же транзакция) → 201
          ├─ бизнес-отказ → FAILED + TransferFailed → 201 со статусом FAILED
          └─ сосед недоступен → остаётся PENDING → 202; PendingOperationReconciler повторит позже
```

Повтор безопасен на любом шаге: Account Service применит `operationId` не более одного раза.

## Кредиты: сага выдачи

```
заявка SUBMITTED
  ├─ скоринг: сумма ≤ autoApproveLimit и платёж ≤ 50% дохода → APPROVED автоматически
  ├─ иначе ждёт решения администратора
  └─ APPROVED → Loan(PENDING_DISBURSEMENT)
        payment-service /internal/operations (LOAN_DISBURSEMENT, operationId = id кредита)
          ├─ COMPLETED → Loan ACTIVE, график платежей
          ├─ FAILED    → компенсация: Loan CANCELLED, заявка REJECTED с причиной
          └─ PENDING/сбой → BackgroundJobs повторяет каждые 15 секунд
```

Погашение (`NEXT` — очередной платёж, `FULL` — весь остаток долга) записывается как `LoanPayment`
с собственным `operationId`, поэтому повтор не спишет деньги дважды.

## Вклады

- Открытие: списание со счёта клиента на системный счёт (`DEPOSIT_OPEN`), вклад `OPENING → ACTIVE`.
- Начисление: ежедневно (cron `0 5 * * * *` по Москве догоняет все пропущенные дни),
  `проценты = база × ставка / 365`, запись в `interest_accruals` уникальна по (вклад, день).
  При капитализации база растёт на начисленные проценты.
- Закрытие в срок — выплата тела и всех процентов; досрочное — проценты пересчитываются по `earlyCloseRate`.
- Администратор может «промотать» время: `POST /api/admin/deposits/simulate-days?days=N`.

## События и уведомления

Все производители пишут события через `OutboxWriter` (`Propagation.MANDATORY`: событие нельзя записать вне транзакции
бизнес-изменения). `OutboxPublisher` раз в секунду забирает до 100 неопубликованных записей
(`FOR UPDATE SKIP LOCKED`) и отправляет их в Kafka с ключом `userId`, поэтому события одного пользователя
попадают в одну партицию и обрабатываются по порядку.

Формат событий и список топиков — в [events.md](events.md).

## Кэширование (Redis)

| Кэш | Где | TTL | Инвалидация |
|---|---|---|---|
| `exchange-rates` | payment-service | 1 ч | курсы задаются конфигурацией |
| `loan-products`, `deposit-products` | loan-service | 10 мин | `@CacheEvict` при изменении продукта администратором |

При недоступности Redis кэш не ломает запросы: `LoggingCacheErrorHandler` логирует ошибку, и данные читаются из БД.
Балансы и история в Redis не хранятся.

## Отказоустойчивость

| Ситуация | Поведение |
|---|---|
| Account Service недоступен при переводе | операция `PENDING` (202), реконсилятор доводит её до конца |
| Payment Service недоступен при выдаче кредита | кредит `PENDING_DISBURSEMENT`, повтор каждые 15 с |
| Kafka недоступна | события копятся в outbox и уходят после восстановления |
| Повторная доставка события | уведомление не дублируется (`ON CONFLICT DO NOTHING`) |
| Событие не обрабатывается | 3 попытки с экспоненциальной паузой, затем топик `*.dlt` |
| Двойной клик «Перевести» | тот же `Idempotency-Key` → та же операция |
