package ru.bibank.loan.dto;

import ru.bibank.loan.entity.Deposit;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record DepositResponse(
        UUID id,
        UUID productId,
        String productName,
        UUID accountId,
        String currency,
        BigDecimal principal,
        BigDecimal annualRate,
        BigDecimal earlyCloseRate,
        boolean capitalization,
        int termMonths,
        LocalDate openDate,
        LocalDate maturityDate,
        LocalDate accruedThrough,
        BigDecimal accruedInterest,
        BigDecimal currentValue,
        BigDecimal projectedInterest,
        String status,
        boolean earlyClosed,
        BigDecimal payoutAmount,
        BigDecimal payoutInterest,
        String failureReason,
        Instant createdAt,
        Instant closedAt,
        List<AccrualResponse> accruals
) {
    public record AccrualResponse(LocalDate date, BigDecimal base, BigDecimal amount) {
    }

    public static DepositResponse from(Deposit d, BigDecimal projectedInterest, List<AccrualResponse> accruals) {
        return new DepositResponse(d.getId(), d.getProductId(), d.getProductName(), d.getAccountId(), d.getCurrency(),
                d.getPrincipal(), d.getAnnualRate(), d.getEarlyCloseRate(), d.isCapitalization(), d.getTermMonths(),
                d.getOpenDate(), d.getMaturityDate(), d.getAccruedThrough(), d.getAccruedInterest(),
                d.getPrincipal().add(d.getAccruedInterest()), projectedInterest, d.getStatus().name(),
                d.isEarlyClosed(), d.getPayoutAmount(), d.getPayoutInterest(), d.getFailureReason(),
                d.getCreatedAt(), d.getClosedAt(), accruals);
    }
}
