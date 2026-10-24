package ru.bibank.payment.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import ru.bibank.common.client.InternalRestClient;
import ru.bibank.common.security.SecurityProperties;
import ru.bibank.payment.config.PaymentProperties;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * Клиент внутреннего API Account Service. Балансы меняет только Account Service.
 */
@Component
public class AccountClient {

    private final InternalRestClient client;

    public AccountClient(PaymentProperties properties, SecurityProperties security, ObjectMapper objectMapper) {
        this.client = new InternalRestClient("account-service", properties.accountServiceUrl(),
                security.internalToken(), objectMapper, Duration.ofSeconds(5));
    }

    public AccountInfo getById(UUID id) {
        return client.call(c -> c.get().uri("/internal/accounts/{id}", id).retrieve().body(AccountInfo.class));
    }

    public AccountInfo getByNumber(String number) {
        return client.call(c -> c.get().uri("/internal/accounts/by-number/{n}", number).retrieve().body(AccountInfo.class));
    }

    public AccountInfo systemAccount(String currency) {
        return client.call(c -> c.get().uri("/internal/accounts/system/{c}", currency).retrieve().body(AccountInfo.class));
    }

    public OperationResult apply(UUID operationId, String type, List<Posting> postings) {
        var body = new OperationRequest(operationId, type, postings);
        return client.call(c -> c.post().uri("/internal/operations").body(body).retrieve().body(OperationResult.class));
    }

    public record Posting(UUID accountId, BigDecimal amount, Long expectedOwnerId) {
    }

    public record OperationRequest(UUID operationId, String type, List<Posting> postings) {
    }

    public record OperationResult(UUID operationId, boolean alreadyApplied, List<AppliedPosting> postings) {
    }

    public record AppliedPosting(UUID accountId, BigDecimal amount, BigDecimal balanceAfter) {
    }
}
