package ru.bibank.payment.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

public record ExchangeRatesResponse(String base, Map<String, BigDecimal> rates, Instant updatedAt)
        implements java.io.Serializable {
}
