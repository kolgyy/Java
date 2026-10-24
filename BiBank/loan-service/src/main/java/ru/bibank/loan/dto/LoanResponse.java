package ru.bibank.loan.dto;

import ru.bibank.loan.entity.Loan;
import ru.bibank.loan.entity.ScheduleItem;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record LoanResponse(
        UUID id,
        UUID applicationId,
        String productName,
        UUID accountId,
        String currency,
        BigDecimal principal,
        BigDecimal annualRate,
        int termMonths,
        BigDecimal monthlyPayment,
        BigDecimal outstandingPrincipal,
        BigDecimal totalRemainingPayments,
        int paidInstallments,
        LocalDate nextPaymentDate,
        BigDecimal nextPaymentAmount,
        String status,
        Instant issuedAt,
        Instant closedAt,
        List<ScheduleItemResponse> schedule,
        List<PaymentResponse> payments
) {
    public static LoanResponse from(Loan l, boolean withDetails, List<PaymentResponse> payments) {
        List<ScheduleItem> planned = l.getSchedule().stream()
                .filter(i -> i.getStatus() == ScheduleItem.Status.PLANNED).toList();
        ScheduleItem next = planned.isEmpty() ? null : planned.get(0);
        BigDecimal remaining = planned.stream().map(ScheduleItem::getPaymentAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        List<ScheduleItemResponse> schedule = withDetails
                ? l.getSchedule().stream().map(i -> new ScheduleItemResponse(i.getInstallmentNumber(), i.getDueDate(),
                i.getPaymentAmount(), i.getPrincipalPart(), i.getInterestPart(), i.getRemainingPrincipal(),
                i.getStatus().name())).toList()
                : null;
        return new LoanResponse(l.getId(), l.getApplicationId(), l.getProductName(), l.getAccountId(), l.getCurrency(),
                l.getPrincipal(), l.getAnnualRate(), l.getTermMonths(), l.getMonthlyPayment(),
                l.getOutstandingPrincipal(), remaining, l.getSchedule().size() - planned.size(),
                next == null ? null : next.getDueDate(), next == null ? null : next.getPaymentAmount(),
                l.getStatus().name(), l.getIssuedAt(), l.getClosedAt(), schedule, payments);
    }
}
