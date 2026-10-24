package ru.bibank.payment.dto;

import java.math.BigDecimal;
import java.util.Map;

public record PaymentStatsResponse(
        long totalOperations,
        long pending,
        long completed,
        long failed,
        Map<String, Map<String, Long>> byTypeAndStatus,
        Map<String, BigDecimal> transferVolumeByCurrency
) {
}
