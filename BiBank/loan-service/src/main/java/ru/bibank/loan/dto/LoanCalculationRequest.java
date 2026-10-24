package ru.bibank.loan.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record LoanCalculationRequest(
        @NotNull UUID productId,
        @NotNull @DecimalMin("1.00") @Digits(integer = 15, fraction = 2) BigDecimal amount,
        @NotNull @Min(1) Integer termMonths
) {
}
