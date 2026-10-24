package ru.bibank.loan.dto;

import ru.bibank.loan.entity.LoanProduct;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.UUID;

public record LoanProductResponse(
        UUID id, String code, String name, String description, String currency, BigDecimal annualRate,
        BigDecimal minAmount, BigDecimal maxAmount, int minTermMonths, int maxTermMonths,
        BigDecimal autoApproveLimit, boolean active
) implements Serializable {
    public static LoanProductResponse from(LoanProduct p) {
        return new LoanProductResponse(p.getId(), p.getCode(), p.getName(), p.getDescription(), p.getCurrency(),
                p.getAnnualRate(), p.getMinAmount(), p.getMaxAmount(), p.getMinTermMonths(), p.getMaxTermMonths(),
                p.getAutoApproveLimit(), p.isActive());
    }
}
