package ru.bibank.payment.dto;

import ru.bibank.payment.entity.Operation;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Операция с точки зрения конкретного пользователя.
 *
 * @param direction OUT — списание, IN — зачисление, INTERNAL — между своими счетами
 */
public record OperationResponse(
        UUID id,
        String type,
        String status,
        String direction,
        UUID fromAccountId,
        String fromAccountNumber,
        UUID toAccountId,
        String toAccountNumber,
        String counterpartyName,
        BigDecimal amount,
        String currency,
        BigDecimal creditedAmount,
        String creditedCurrency,
        BigDecimal exchangeRate,
        String description,
        String failureCode,
        String failureReason,
        Instant createdAt,
        Instant completedAt
) {
    public static OperationResponse from(Operation o, Long viewerId) {
        boolean out = viewerId != null && viewerId.equals(o.getFromOwnerId());
        boolean in = viewerId != null && viewerId.equals(o.getToOwnerId());
        String direction = out && in ? "INTERNAL" : in ? "IN" : "OUT";
        return new OperationResponse(o.getId(), o.getType().name(), o.getStatus().name(), direction,
                o.getFromAccountId(), o.getFromAccountNumber(), o.getToAccountId(), o.getToAccountNumber(),
                o.getToOwnerName(), o.getAmount(), o.getCurrency(), o.getCreditedAmount(), o.getCreditedCurrency(),
                o.getExchangeRate(), o.getDescription(), o.getFailureCode(), o.getFailureReason(),
                o.getCreatedAt(), o.getCompletedAt());
    }
}
