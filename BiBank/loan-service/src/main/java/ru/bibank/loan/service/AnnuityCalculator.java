package ru.bibank.loan.service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Аннуитетный график: равные ежемесячные платежи, проценты начисляются на остаток долга.
 * Последний платёж корректируется, чтобы долг был погашен ровно до нуля.
 */
public final class AnnuityCalculator {

    private static final MathContext MC = new MathContext(20, RoundingMode.HALF_EVEN);

    public record Installment(int number, LocalDate dueDate, BigDecimal payment, BigDecimal principal,
                              BigDecimal interest, BigDecimal remaining) {
    }

    public record Schedule(BigDecimal monthlyPayment, BigDecimal totalPayment, BigDecimal totalInterest,
                           List<Installment> installments) {
    }

    private AnnuityCalculator() {
    }

    public static BigDecimal monthlyPayment(BigDecimal principal, BigDecimal annualRatePercent, int months) {
        BigDecimal r = monthlyRate(annualRatePercent);
        if (r.signum() == 0) {
            return principal.divide(BigDecimal.valueOf(months), 2, RoundingMode.HALF_EVEN);
        }
        // A = P * r / (1 - (1 + r)^-n)
        BigDecimal pow = BigDecimal.ONE.add(r).pow(months, MC);
        BigDecimal annuity = principal.multiply(r, MC).multiply(pow, MC).divide(pow.subtract(BigDecimal.ONE), MC);
        return annuity.setScale(2, RoundingMode.HALF_EVEN);
    }

    public static Schedule schedule(BigDecimal principal, BigDecimal annualRatePercent, int months,
                                    LocalDate firstPaymentDate) {
        BigDecimal r = monthlyRate(annualRatePercent);
        BigDecimal payment = monthlyPayment(principal, annualRatePercent, months);
        BigDecimal remaining = principal;
        BigDecimal totalInterest = BigDecimal.ZERO;
        BigDecimal totalPayment = BigDecimal.ZERO;
        List<Installment> items = new ArrayList<>(months);

        for (int i = 1; i <= months; i++) {
            BigDecimal interest = remaining.multiply(r, MC).setScale(2, RoundingMode.HALF_EVEN);
            BigDecimal principalPart;
            BigDecimal amount;
            if (i == months) {
                principalPart = remaining;
                amount = principalPart.add(interest);
            } else {
                principalPart = payment.subtract(interest);
                amount = payment;
            }
            remaining = remaining.subtract(principalPart);
            totalInterest = totalInterest.add(interest);
            totalPayment = totalPayment.add(amount);
            items.add(new Installment(i, firstPaymentDate.plusMonths(i - 1L), amount, principalPart, interest, remaining));
        }
        return new Schedule(payment, totalPayment, totalInterest, items);
    }

    private static BigDecimal monthlyRate(BigDecimal annualRatePercent) {
        return annualRatePercent.divide(BigDecimal.valueOf(1200), MC);
    }
}
