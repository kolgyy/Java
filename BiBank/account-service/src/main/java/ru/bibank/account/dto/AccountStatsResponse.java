package ru.bibank.account.dto;

import java.math.BigDecimal;
import java.util.List;

public record AccountStatsResponse(long totalAccounts, List<CurrencyStats> byCurrency) {

    public record CurrencyStats(String currency, long accounts, BigDecimal totalBalance) {
    }
}
