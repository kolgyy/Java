package ru.bibank.loan.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.util.UUID;

public record CreateApplicationRequest(
        @NotNull(message = "Выберите кредитный продукт") UUID productId,
        @NotNull(message = "Выберите счёт для зачисления") UUID accountId,
        @NotNull(message = "Укажите сумму")
        @DecimalMin(value = "1.00", message = "Сумма должна быть больше нуля")
        @Digits(integer = 15, fraction = 2) BigDecimal amount,
        @NotNull(message = "Укажите срок") @Min(1) @Max(360) Integer termMonths,
        @DecimalMin("0.00") @Digits(integer = 15, fraction = 2) BigDecimal monthlyIncome,
        @Size(max = 255) String purpose
) {
}
