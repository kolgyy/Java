package ru.bibank.loan.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;

import java.math.BigDecimal;

public record UpdateProductRequest(
        @DecimalMin("0.0") @DecimalMax("100.0") BigDecimal annualRate,
        Boolean active
) {
}
