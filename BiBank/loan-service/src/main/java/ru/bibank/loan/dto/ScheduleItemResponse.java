package ru.bibank.loan.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ScheduleItemResponse(
        int number,
        LocalDate dueDate,
        BigDecimal payment,
        BigDecimal principal,
        BigDecimal interest,
        BigDecimal remaining,
        String status
) {
}
