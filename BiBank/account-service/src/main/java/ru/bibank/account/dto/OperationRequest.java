package ru.bibank.account.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Атомарная операция над балансами: набор проводок, применяемых в одной транзакции.
 *
 * @param operationId ключ идемпотентности: повторный запрос с тем же id не изменяет балансы
 */
public record OperationRequest(
        @NotNull UUID operationId,
        @NotBlank String type,
        @NotEmpty @Valid List<Posting> postings
) {
    /**
     * @param amount          отрицательная сумма — списание, положительная — зачисление
     * @param expectedOwnerId если задан, счёт обязан принадлежать этому пользователю
     */
    public record Posting(
            @NotNull UUID accountId,
            @NotNull BigDecimal amount,
            Long expectedOwnerId
    ) {
    }
}
