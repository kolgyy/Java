package ru.bibank.loan.dto;

import ru.bibank.loan.entity.DepositProduct;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.UUID;

public record DepositProductResponse(
        UUID id, String code, String name, String description, String currency, BigDecimal annualRate,
        BigDecimal earlyCloseRate, BigDecimal minAmount, BigDecimal maxAmount, int minTermMonths,
        int maxTermMonths, boolean capitalization, boolean active
) implements Serializable {
    public static DepositProductResponse from(DepositProduct p) {
        return new DepositProductResponse(p.getId(), p.getCode(), p.getName(), p.getDescription(), p.getCurrency(),
                p.getAnnualRate(), p.getEarlyCloseRate(), p.getMinAmount(), p.getMaxAmount(), p.getMinTermMonths(),
                p.getMaxTermMonths(), p.isCapitalization(), p.isActive());
    }
}
