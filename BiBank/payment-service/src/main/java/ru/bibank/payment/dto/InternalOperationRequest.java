package ru.bibank.payment.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import ru.bibank.payment.entity.OperationType;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Операция, инициированная другим сервисом (Loan Service): выдача и погашение кредита,
 * открытие и закрытие вклада. Пустой счёт означает технический счёт банка.
 *
 * @param operationId ключ идемпотентности, задаётся вызывающим сервисом
 */
public record InternalOperationRequest(
        @NotNull UUID operationId,
        @NotNull OperationType type,
        @NotNull Long userId,
        UUID fromAccountId,
        UUID toAccountId,
        @NotNull @DecimalMin("0.01") BigDecimal amount,
        String description,
        String externalRef
) {
}
