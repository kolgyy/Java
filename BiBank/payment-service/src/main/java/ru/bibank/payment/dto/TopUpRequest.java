package ru.bibank.payment.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record TopUpRequest(
        @NotNull UUID accountId,
        @NotNull
        @DecimalMin(value = "0.01", message = "Минимальная сумма 0.01")
        @Digits(integer = 15, fraction = 2, message = "Не более двух знаков после запятой")
        BigDecimal amount
) {
}
