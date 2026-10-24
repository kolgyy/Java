package ru.bibank.payment.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;

/**
 * @param accountServiceUrl адрес Account Service
 * @param maxTransferAmount максимальная сумма одного перевода
 * @param maxTopUpAmount    максимальная сумма демо-пополнения
 * @param ratesToRub        курсы валют к рублю (виртуальные)
 * @param retryDelay        через сколько повторять незавершённые операции
 * @param maxAttempts       максимальное число попыток применения операции
 */
@ConfigurationProperties(prefix = "bibank.payment")
public record PaymentProperties(
        String accountServiceUrl,
        BigDecimal maxTransferAmount,
        BigDecimal maxTopUpAmount,
        Map<String, BigDecimal> ratesToRub,
        Duration retryDelay,
        int maxAttempts
) {
}
