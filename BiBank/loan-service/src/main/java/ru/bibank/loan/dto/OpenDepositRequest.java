package ru.bibank.loan.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.util.UUID;

public record OpenDepositRequest(
        @NotNull(message = "Выберите продукт") UUID productId,
        @NotNull(message = "Выберите счёт списания") UUID accountId,
        @NotNull(message = "Укажите сумму")
        @DecimalMin(value = "1.00") @Digits(integer = 15, fraction = 2) BigDecimal amount,
        @NotNull(message = "Укажите срок") @Min(1) @Max(120) Integer termMonths
) {
}
