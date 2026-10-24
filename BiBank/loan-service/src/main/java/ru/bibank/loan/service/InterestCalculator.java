package ru.bibank.loan.service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Начисление процентов по вкладам: ежедневно, база 365 дней.
 */
public final class InterestCalculator {

    private static final MathContext MC = new MathContext(20, RoundingMode.HALF_EVEN);
    private static final BigDecimal DAYS_IN_YEAR_PERCENT = BigDecimal.valueOf(36500);

    private InterestCalculator() {
    }

    public static BigDecimal daily(BigDecimal base, BigDecimal annualRatePercent) {
        return base.multiply(annualRatePercent, MC).divide(DAYS_IN_YEAR_PERCENT, MC).setScale(2, RoundingMode.HALF_EVEN);
    }

    /**
     * Простые проценты за период — используются при досрочном закрытии по пониженной ставке.
     */
    public static BigDecimal simple(BigDecimal principal, BigDecimal annualRatePercent, LocalDate from, LocalDate to) {
        long days = Math.max(0, ChronoUnit.DAYS.between(from, to));
        return principal.multiply(annualRatePercent, MC).multiply(BigDecimal.valueOf(days), MC)
                .divide(DAYS_IN_YEAR_PERCENT, MC).setScale(2, RoundingMode.HALF_EVEN);
    }

    /**
     * Ожидаемый доход за весь срок (для витрины продукта).
     */
    public static BigDecimal projected(BigDecimal principal, BigDecimal annualRatePercent, int months,
                                       boolean capitalization, LocalDate start) {
        LocalDate end = start.plusMonths(months);
        if (!capitalization) {
            return simple(principal, annualRatePercent, start, end);
        }
        BigDecimal base = principal;
        BigDecimal total = BigDecimal.ZERO;
        for (LocalDate d = start; d.isBefore(end); d = d.plusDays(1)) {
            BigDecimal interest = daily(base, annualRatePercent);
            total = total.add(interest);
            base = base.add(interest);
        }
        return total;
    }
}
