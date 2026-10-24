package ru.bibank.payment.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import ru.bibank.common.client.InternalRestClient.DownstreamUnavailableException;
import ru.bibank.common.events.EventEnvelope;
import ru.bibank.common.events.EventTypes;
import ru.bibank.common.events.Topics;
import ru.bibank.common.outbox.OutboxWriter;
import ru.bibank.common.web.ApiException;
import ru.bibank.payment.client.AccountClient;
import ru.bibank.payment.client.AccountClient.Posting;
import ru.bibank.payment.entity.Operation;
import ru.bibank.payment.entity.OperationType;
import ru.bibank.payment.repository.OperationRepository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Применяет сохранённую операцию к балансам и фиксирует результат.
 * <p>
 * Схема: операция сохраняется в статусе PENDING → вызов Account Service с operationId
 * в качестве ключа идемпотентности → COMPLETED/FAILED + событие в outbox в одной транзакции.
 * Если Account Service недоступен, операция остаётся PENDING и повторяется {@link PendingOperationReconciler}:
 * повтор безопасен, потому что Account Service не применяет одну операцию дважды.
 */
@Service
public class OperationExecutor {

    private static final Logger log = LoggerFactory.getLogger(OperationExecutor.class);

    private final AccountClient accountClient;
    private final OperationRepository repository;
    private final OutboxWriter outboxWriter;
    private final TransactionTemplate tx;
    private final Map<String, UUID> systemAccounts = new ConcurrentHashMap<>();

    public OperationExecutor(AccountClient accountClient, OperationRepository repository,
                             OutboxWriter outboxWriter, TransactionTemplate tx) {
        this.accountClient = accountClient;
        this.repository = repository;
        this.outboxWriter = outboxWriter;
        this.tx = tx;
    }

    public Operation execute(UUID operationId) {
        Operation operation = repository.findById(operationId).orElseThrow();
        if (operation.getStatus().isFinal()) {
            return operation;
        }
        try {
            accountClient.apply(operation.getId(), operation.getType().name(), postings(operation));
            return finish(operationId, null, null);
        } catch (DownstreamUnavailableException e) {
            log.warn("Operation {} left PENDING: {}", operationId, e.getMessage());
            return tx.execute(s -> {
                Operation op = repository.findById(operationId).orElseThrow();
                op.setAttempts(op.getAttempts() + 1);
                return op;
            });
        } catch (ApiException e) {
            return finish(operationId, e.getCode(), e.getMessage());
        }
    }

    private Operation finish(UUID operationId, String failureCode, String failureReason) {
        return tx.execute(status -> {
            Operation op = repository.findById(operationId).orElseThrow();
            if (op.getStatus().isFinal()) {
                return op;
            }
            op.setAttempts(op.getAttempts() + 1);
            if (failureCode == null) {
                op.complete();
            } else {
                op.fail(failureCode, failureReason);
            }
            publishEvents(op);
            return op;
        });
    }

    List<Posting> postings(Operation op) {
        List<Posting> postings = new ArrayList<>();
        UUID from = op.getFromAccountId() != null ? op.getFromAccountId() : system(op.getCurrency());
        UUID to = op.getToAccountId() != null ? op.getToAccountId() : system(op.getCreditedCurrency());
        Long expectedOwner = op.getFromAccountId() != null ? op.getFromOwnerId() : null;

        postings.add(new Posting(from, op.getAmount().negate(), expectedOwner));
        if (!op.getCurrency().equals(op.getCreditedCurrency())) {
            // Конвертация через технические счета банка: сумма проводок по каждой валюте равна нулю
            postings.add(new Posting(system(op.getCurrency()), op.getAmount(), null));
            postings.add(new Posting(system(op.getCreditedCurrency()), op.getCreditedAmount().negate(), null));
        }
        postings.add(new Posting(to, op.getCreditedAmount(), null));
        return postings;
    }

    UUID system(String currency) {
        return systemAccounts.computeIfAbsent(currency, c -> accountClient.systemAccount(c).id());
    }

    private void publishEvents(Operation op) {
        if (op.getType() == OperationType.TRANSFER) {
            if (op.getStatus() == ru.bibank.payment.entity.OperationStatus.COMPLETED) {
                Map<String, Object> payload = basePayload(op);
                payload.put("toAccountNumber", op.getToAccountNumber());
                payload.put("recipientName", nullToEmpty(op.getToOwnerName()));
                outboxWriter.write(Topics.PAYMENT_EVENTS, EventEnvelope.of(EventTypes.TRANSFER_COMPLETED,
                        "payment-service", op.getFromOwnerId(), op.getId(), payload));

                if (!op.getFromOwnerId().equals(op.getToOwnerId())) {
                    Map<String, Object> received = basePayload(op);
                    received.put("fromAccountNumber", op.getFromAccountNumber());
                    received.put("senderName", nullToEmpty(op.getFromOwnerName()));
                    outboxWriter.write(Topics.PAYMENT_EVENTS, EventEnvelope.of(EventTypes.TRANSFER_RECEIVED,
                            "payment-service", op.getToOwnerId(), op.getId(), received));
                }
            } else {
                Map<String, Object> payload = basePayload(op);
                payload.put("reason", nullToEmpty(op.getFailureReason()));
                payload.put("code", nullToEmpty(op.getFailureCode()));
                outboxWriter.write(Topics.PAYMENT_EVENTS, EventEnvelope.of(EventTypes.TRANSFER_FAILED,
                        "payment-service", op.getFromOwnerId(), op.getId(), payload));
            }
        } else if (op.getType() == OperationType.TOP_UP
                && op.getStatus() == ru.bibank.payment.entity.OperationStatus.COMPLETED) {
            Map<String, Object> payload = basePayload(op);
            payload.put("toAccountNumber", op.getToAccountNumber());
            outboxWriter.write(Topics.PAYMENT_EVENTS, EventEnvelope.of(EventTypes.TOP_UP_COMPLETED,
                    "payment-service", op.getToOwnerId(), op.getId(), payload));
        }
        // События по кредитам и вкладам публикует Loan Service
    }

    private static Map<String, Object> basePayload(Operation op) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("operationId", op.getId().toString());
        payload.put("amount", op.getAmount());
        payload.put("currency", op.getCurrency());
        payload.put("creditedAmount", op.getCreditedAmount());
        payload.put("creditedCurrency", op.getCreditedCurrency());
        return payload;
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
