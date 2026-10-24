# События Kafka

## Топики

| Топик | Производитель | Ключ | Партиции |
|---|---|---|---|
| `bibank.user-events` | auth-service | userId | 3 |
| `bibank.account-events` | account-service | userId | 3 |
| `bibank.payment-events` | payment-service | userId | 3 |
| `bibank.loan-events` | loan-service | userId | 3 |
| `<топик>.dlt` | notification-service (DeadLetterPublishingRecoverer) | исходный | 3 |

Топики создаются приложениями через `KafkaAdmin.NewTopics` (автосоздание на брокере не используется).
Потребитель — `notification-service`, группа `notification-service`, `ack-mode: record`, `auto-offset-reset: earliest`.

## Формат сообщения

Значение — JSON `EventEnvelope`:

```json
{
  "eventId": "6c1f…",            // UUID, ключ дедупликации у потребителя
  "eventType": "TransferCompleted",
  "source": "payment-service",
  "userId": 42,                  // кому адресовано событие
  "aggregateId": "f3a0…",        // id операции/счёта/кредита
  "occurredAt": "2026-10-23T20:50:00Z",
  "payload": { "...": "..." }
}
```

## Типы событий

| eventType | Источник | Когда | Основные поля payload |
|---|---|---|---|
| `UserRegistered` | auth | регистрация | email, fullName |
| `UserStatusChanged` | auth | блокировка/разблокировка | status |
| `AccountCreated` | account | открыт счёт | accountId, number, currency, type |
| `AccountStatusChanged` | account | заморозка/разморозка/закрытие | accountId, number, oldStatus, newStatus |
| `TransferCompleted` | payment | перевод выполнен (отправителю) | amount, currency, toAccountNumber, recipientName |
| `TransferReceived` | payment | перевод выполнен (получателю, если это другой человек) | creditedAmount, creditedCurrency, fromAccountNumber, senderName |
| `TransferFailed` | payment | перевод отклонён | amount, currency, code, reason |
| `TopUpCompleted` | payment | пополнение | amount, currency, toAccountNumber |
| `LoanApplicationSubmitted` | loan | заявка создана | productName, amount, currency, termMonths |
| `LoanApplicationStatusChanged` | loan | одобрена/отклонена/отменена | applicationId, status, amount, currency, productName, reason |
| `LoanPaymentMade` | loan | платёж по кредиту | amount, currency, remaining |
| `LoanClosed` | loan | кредит погашен | productName |
| `DepositOpened` | loan | вклад открыт | productName, amount, currency, rate, maturityDate |
| `DepositClosed` | loan | вклад закрыт | productName, payout, interest, currency, early |

## Гарантии

- **Публикация:** at-least-once через Transactional Outbox. Событие появляется в Kafka только если бизнес-транзакция зафиксирована.
- **Порядок:** в пределах пользователя (ключ = userId).
- **Обработка:** идемпотентная. Уникальный индекс `notifications(event_id, user_id)`, вставка `ON CONFLICT DO NOTHING`.
- **Ошибки:** `DefaultErrorHandler` с `ExponentialBackOff(500 мс, ×2, 3 попытки)`, затем `<топик>.dlt`.
  Посмотреть DLT удобно в Kafka UI (`docker compose --profile tools up -d kafka-ui`).
