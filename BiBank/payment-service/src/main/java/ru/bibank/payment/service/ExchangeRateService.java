package ru.bibank.payment.service;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import ru.bibank.common.web.ApiException;
import ru.bibank.payment.config.PaymentProperties;
import ru.bibank.payment.dto.ExchangeRatesResponse;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Map;
import java.util.TreeMap;

/**
 * Виртуальные курсы валют. Справочник кэшируется в Redis (TTL задаётся spring.cache.redis.time-to-live).
 */
@Service
public class ExchangeRateService {

    private final PaymentProperties properties;

    public ExchangeRateService(PaymentProperties properties) {
        this.properties = properties;
    }

    @Cacheable(cacheNames = "exchange-rates", key = "'all'")
    public ExchangeRatesResponse rates() {
        Map<String, BigDecimal> rates = new TreeMap<>(properties.ratesToRub());
        rates.put("RUB", BigDecimal.ONE);
        return new ExchangeRatesResponse("RUB", rates, Instant.now());
    }

    /**
     * Курс пересчёта from → to (сколько единиц to за одну единицу from).
     */
    public BigDecimal rate(String from, String to) {
        if (from.equals(to)) {
            return BigDecimal.ONE;
        }
        return toRub(from).divide(toRub(to), 8, RoundingMode.HALF_EVEN);
    }

    public BigDecimal convert(BigDecimal amount, String from, String to) {
        if (from.equals(to)) {
            return amount;
        }
        // Конвертация через рубль без промежуточного округления курса
        return amount.multiply(toRub(from)).divide(toRub(to), 2, RoundingMode.HALF_EVEN);
    }

    private BigDecimal toRub(String currency) {
        if ("RUB".equals(currency)) {
            return BigDecimal.ONE;
        }
        BigDecimal rate = properties.ratesToRub().get(currency);
        if (rate == null) {
            throw ApiException.badRequest("UNSUPPORTED_CURRENCY", "Валюта " + currency + " не поддерживается");
        }
        return rate;
    }
}
