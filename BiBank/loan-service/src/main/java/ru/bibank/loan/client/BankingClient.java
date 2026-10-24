package ru.bibank.loan.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import ru.bibank.common.client.InternalRestClient;
import ru.bibank.common.client.InternalRestClient.DownstreamUnavailableException;
import ru.bibank.common.security.SecurityProperties;
import ru.bibank.common.web.ApiException;
import ru.bibank.loan.config.LoanProperties;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.UUID;

/**
 * Обращения к Account Service (проверка счёта) и Payment Service (движение денег).
 * Loan Service не меняет балансы напрямую.
 */
@Component
public class BankingClient {

    public enum OperationType { LOAN_DISBURSEMENT, LOAN_REPAYMENT, DEPOSIT_OPEN, DEPOSIT_CLOSE }

    public record AccountInfo(UUID id, String number, Long ownerId, String currency, String type, String status,
                              BigDecimal balance) {
    }

    public record OperationOutcome(UUID id, String status, String failureCode, String failureReason) {

        public static OperationOutcome pending(UUID id) {
            return new OperationOutcome(id, "PENDING", null, null);
        }

        public boolean completed() {
            return "COMPLETED".equals(status);
        }

        public boolean failed() {
            return "FAILED".equals(status);
        }
    }

    private record InternalOperationRequest(UUID operationId, OperationType type, Long userId, UUID fromAccountId,
                                            UUID toAccountId, BigDecimal amount, String description,
                                            String externalRef) {
    }

    private final InternalRestClient accounts;
    private final InternalRestClient payments;

    public BankingClient(LoanProperties properties, SecurityProperties security, ObjectMapper objectMapper) {
        this.accounts = new InternalRestClient("account-service", properties.accountServiceUrl(),
                security.internalToken(), objectMapper, Duration.ofSeconds(5));
        this.payments = new InternalRestClient("payment-service", properties.paymentServiceUrl(),
                security.internalToken(), objectMapper, Duration.ofSeconds(10));
    }

    /**
     * Счёт пользователя, пригодный для операции; чужой счёт неотличим от несуществующего.
     */
    public AccountInfo ownedActiveAccount(Long userId, UUID accountId, String currency) {
        AccountInfo account;
        try {
            account = accounts.call(c -> c.get().uri("/internal/accounts/{id}", accountId)
                    .retrieve().body(AccountInfo.class));
        } catch (ApiException e) {
            if (e.getStatus().value() == 404) {
                throw ApiException.notFound("Счёт не найден");
            }
            throw e;
        }
        if (account == null || !userId.equals(account.ownerId()) || "SYSTEM".equals(account.type())) {
            throw ApiException.notFound("Счёт не найден");
        }
        if (!"ACTIVE".equals(account.status())) {
            throw ApiException.unprocessable("ACCOUNT_NOT_ACTIVE", "Счёт недоступен для операций");
        }
        if (currency != null && !currency.equals(account.currency())) {
            throw ApiException.unprocessable("CURRENCY_MISMATCH",
                    "Валюта счёта должна совпадать с валютой продукта (" + currency + ")");
        }
        return account;
    }

    /**
     * Выполняет операцию через Payment Service. Повтор с тем же operationId безопасен.
     * При недоступности сервиса возвращает PENDING — операцию довыполнит планировщик.
     */
    public OperationOutcome execute(UUID operationId, OperationType type, Long userId, UUID fromAccountId,
                                    UUID toAccountId, BigDecimal amount, String description, String externalRef) {
        var body = new InternalOperationRequest(operationId, type, userId, fromAccountId, toAccountId, amount,
                description, externalRef);
        try {
            return payments.call(c -> c.post().uri("/internal/operations").body(body)
                    .retrieve().body(OperationOutcome.class));
        } catch (DownstreamUnavailableException e) {
            return OperationOutcome.pending(operationId);
        } catch (ApiException e) {
            return new OperationOutcome(operationId, "FAILED", e.getCode(), e.getMessage());
        }
    }
}
