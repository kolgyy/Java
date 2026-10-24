package ru.bibank.loan.dto;

import ru.bibank.loan.entity.LoanPayment;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentResponse(UUID id, UUID accountId, String mode, BigDecimal amount, String status,
                              String failureReason, Instant createdAt, Instant completedAt) {
    public static PaymentResponse from(LoanPayment p) {
        return new PaymentResponse(p.getId(), p.getAccountId(), p.getMode().name(), p.getAmount(), p.getStatus().name(),
                p.getFailureReason(), p.getCreatedAt(), p.getCompletedAt());
    }
}
