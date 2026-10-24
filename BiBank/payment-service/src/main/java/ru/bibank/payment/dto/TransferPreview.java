package ru.bibank.payment.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Данные для экрана подтверждения перевода.
 */
public record TransferPreview(
        UUID fromAccountId,
        String fromAccountNumber,
        String toAccountNumber,
        String recipientName,
        BigDecimal amount,
        String currency,
        BigDecimal creditedAmount,
        String creditedCurrency,
        BigDecimal exchangeRate,
        BigDecimal fee,
        BigDecimal availableBalance,
        boolean sufficientFunds
) {
}
