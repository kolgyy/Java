package ru.bibank.loan.dto;

import java.math.BigDecimal;
import java.util.List;

public record LoanCalculationResponse(
        BigDecimal amount,
        String currency,
        BigDecimal annualRate,
        int termMonths,
        BigDecimal monthlyPayment,
        BigDecimal totalPayment,
        BigDecimal totalInterest,
        List<ScheduleItemResponse> schedule
) {
}
