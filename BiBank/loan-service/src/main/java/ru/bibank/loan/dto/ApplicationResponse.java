package ru.bibank.loan.dto;

import ru.bibank.loan.entity.LoanApplication;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ApplicationResponse(
        UUID id,
        Long userId,
        UUID productId,
        String productName,
        UUID accountId,
        String accountNumber,
        BigDecimal amount,
        String currency,
        int termMonths,
        BigDecimal annualRate,
        BigDecimal monthlyIncome,
        String purpose,
        String status,
        String decisionReason,
        Instant decidedAt,
        UUID loanId,
        Instant createdAt
) {
    public static ApplicationResponse from(LoanApplication a) {
        return new ApplicationResponse(a.getId(), a.getUserId(), a.getProduct().getId(), a.getProduct().getName(),
                a.getAccountId(), a.getAccountNumber(), a.getAmount(), a.getCurrency(), a.getTermMonths(),
                a.getProduct().getAnnualRate(), a.getMonthlyIncome(), a.getPurpose(), a.getStatus().name(),
                a.getDecisionReason(), a.getDecidedAt(), a.getLoanId(), a.getCreatedAt());
    }
}
