package ru.bibank.loan.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class CalculatorsTest {

    @Test
    void annuityPaymentMatchesTheFormula() {
        // 3 000 под 8.5% на 12 месяцев — такой же расчёт показывает интерфейс
        assertThat(AnnuityCalculator.monthlyPayment(new BigDecimal("3000"), new BigDecimal("8.5"), 12))
                .isEqualByComparingTo("261.66");
        assertThat(AnnuityCalculator.monthlyPayment(new BigDecimal("100000"), new BigDecimal("17.9"), 12))
                .isEqualByComparingTo("9163.24");
    }

    @Test
    void scheduleRepaysPrincipalExactlyToZero() {
        BigDecimal principal = new BigDecimal("777777.77");
        var schedule = AnnuityCalculator.schedule(principal, new BigDecimal("12.5"), 37, LocalDate.of(2026, 1, 31));

        assertThat(schedule.installments()).hasSize(37);
        BigDecimal principalSum = schedule.installments().stream()
                .map(AnnuityCalculator.Installment::principal).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(principalSum).isEqualByComparingTo(principal);
        assertThat(schedule.installments().getLast().remaining()).isEqualByComparingTo("0");
        assertThat(schedule.totalPayment()).isEqualByComparingTo(principal.add(schedule.totalInterest()));
        // даты платежей не «уплывают» после коротких месяцев
        assertThat(schedule.installments().get(1).dueDate()).isEqualTo(LocalDate.of(2026, 2, 28));
        assertThat(schedule.installments().get(2).dueDate()).isEqualTo(LocalDate.of(2026, 3, 31));
    }

    @Test
    void zeroRateSplitsPrincipalEvenly() {
        assertThat(AnnuityCalculator.monthlyPayment(new BigDecimal("1200"), BigDecimal.ZERO, 12))
                .isEqualByComparingTo("100.00");
    }

    @Test
    void dailyInterestUsesActual365Basis() {
        assertThat(InterestCalculator.daily(new BigDecimal("100000"), new BigDecimal("18"))).isEqualByComparingTo("49.32");
        assertThat(InterestCalculator.simple(new BigDecimal("100000"), new BigDecimal("18"),
                LocalDate.of(2026, 1, 1), LocalDate.of(2027, 1, 1))).isEqualByComparingTo("18000.00");
    }

    @Test
    void capitalizationEarnsMoreThanSimpleInterest() {
        LocalDate start = LocalDate.of(2026, 1, 1);
        BigDecimal simple = InterestCalculator.projected(new BigDecimal("100000"), new BigDecimal("16"), 12, false, start);
        BigDecimal compound = InterestCalculator.projected(new BigDecimal("100000"), new BigDecimal("16"), 12, true, start);

        assertThat(simple).isEqualByComparingTo("16000.00");
        assertThat(compound).isGreaterThan(simple).isBetween(new BigDecimal("17300"), new BigDecimal("17400"));
    }
}
