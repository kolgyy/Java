package ru.bibank.loan.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "bibank.loan")
public record LoanProperties(String accountServiceUrl, String paymentServiceUrl) {
}
