package ru.bibank.payment.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.support.TransactionTemplate;
import ru.bibank.common.security.AuthenticatedUser;
import ru.bibank.common.web.ApiException;
import ru.bibank.payment.client.AccountClient;
import ru.bibank.payment.client.AccountInfo;
import ru.bibank.payment.config.PaymentProperties;
import ru.bibank.payment.dto.TopUpRequest;
import ru.bibank.payment.dto.TransferRequest;
import ru.bibank.payment.entity.Operation;
import ru.bibank.payment.entity.OperationStatus;
import ru.bibank.payment.entity.OperationType;
import ru.bibank.payment.repository.OperationRepository;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransferServiceTest {

    private static final AuthenticatedUser ALICE = new AuthenticatedUser(1L, "alice@test", "ROLE_USER", "Алиса Иванова");
    private static final String KEY = "6f1d2c1e-key";

    @Mock
    OperationRepository repository;
    @Mock
    AccountClient accountClient;
    @Mock
    OperationExecutor executor;
    @Mock
    TransactionTemplate tx;

    TransferService service;
    final PaymentProperties properties = new PaymentProperties("http://account", new BigDecimal("10000000"),
            new BigDecimal("1000000"), Map.of("USD", new BigDecimal("92.50"), "EUR", new BigDecimal("100.20")),
            Duration.ofSeconds(5), 5);

    final UUID fromId = UUID.randomUUID();
    final AccountInfo from = new AccountInfo(fromId, "40817810000000000001", 1L, "Алиса Иванова", "RUB", "MAIN", "ACTIVE",
            new BigDecimal("5000.00"));

    @BeforeEach
    void setUp() {
        service = new TransferService(repository, accountClient, executor, new ExchangeRateService(properties), properties, tx);
    }

    @Test
    void replayWithSameKeyReturnsExistingOperationWithoutMovingMoney() {
        Operation existing = transfer(fromId, "100.00", OperationStatus.COMPLETED);
        when(repository.findByInitiatorIdAndIdempotencyKey(1L, KEY)).thenReturn(Optional.of(existing));

        TransferService.Result result = service.transfer(ALICE, KEY, request(fromId, "100.00"));

        assertThat(result.replayed()).isTrue();
        assertThat(result.operation()).isSameAs(existing);
        verifyNoInteractions(accountClient, executor);
    }

    @Test
    void replayOfPendingOperationFinishesIt() {
        Operation existing = transfer(fromId, "100.00", OperationStatus.PENDING);
        Operation completed = transfer(fromId, "100.00", OperationStatus.COMPLETED);
        when(repository.findByInitiatorIdAndIdempotencyKey(1L, KEY)).thenReturn(Optional.of(existing));
        when(executor.execute(existing.getId())).thenReturn(completed);

        TransferService.Result result = service.transfer(ALICE, KEY, request(fromId, "100.00"));

        assertThat(result.operation().getStatus()).isEqualTo(OperationStatus.COMPLETED);
        assertThat(result.replayed()).isTrue();
    }

    @Test
    void reusingKeyForDifferentAmountIsRejected() {
        when(repository.findByInitiatorIdAndIdempotencyKey(1L, KEY))
                .thenReturn(Optional.of(transfer(fromId, "100.00", OperationStatus.COMPLETED)));

        assertThatThrownBy(() -> service.transfer(ALICE, KEY, request(fromId, "150.00")))
                .isInstanceOf(ApiException.class)
                .extracting("code").isEqualTo("IDEMPOTENCY_KEY_REUSED");
        verifyNoInteractions(executor);
    }

    @Test
    void missingKeyIsRejected() {
        assertThatThrownBy(() -> service.transfer(ALICE, " ", request(fromId, "1.00")))
                .isInstanceOf(ApiException.class)
                .extracting("code").isEqualTo("IDEMPOTENCY_KEY_REQUIRED");
    }

    @Test
    void amountAboveLimitIsRejectedBeforeAnyRemoteCall() {
        when(repository.findByInitiatorIdAndIdempotencyKey(1L, KEY)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.topUp(ALICE, KEY, new TopUpRequest(fromId, new BigDecimal("1000000.01"))))
                .isInstanceOf(ApiException.class)
                .extracting("code").isEqualTo("AMOUNT_LIMIT_EXCEEDED");
        verifyNoInteractions(accountClient, executor);
    }

    @Test
    void foreignAccountLooksLikeMissingAccount() {
        when(repository.findByInitiatorIdAndIdempotencyKey(1L, KEY)).thenReturn(Optional.empty());
        AccountInfo foreign = new AccountInfo(fromId, "40817810000000000001", 2L, "Борис", "RUB", "MAIN", "ACTIVE", BigDecimal.TEN);
        when(accountClient.getById(fromId)).thenReturn(foreign);

        assertThatThrownBy(() -> service.transfer(ALICE, KEY, request(fromId, "1.00")))
                .isInstanceOf(ApiException.class)
                .extracting("status.value").isEqualTo(404);
        verify(executor, never()).execute(any());
    }

    @Test
    void concurrentDuplicateResolvesToTheWinningOperation() {
        UUID toId = UUID.randomUUID();
        AccountInfo to = new AccountInfo(toId, "40817840000000000002", 2L, "Борис Петров", "USD", "MAIN", "ACTIVE", BigDecimal.ZERO);
        Operation winner = transfer(fromId, "9250.00", OperationStatus.COMPLETED);
        when(repository.findByInitiatorIdAndIdempotencyKey(1L, KEY)).thenReturn(Optional.empty(), Optional.of(winner));
        when(accountClient.getById(fromId)).thenReturn(from);
        when(accountClient.getByNumber(to.number())).thenReturn(to);
        doThrow(new DataIntegrityViolationException("uk_operations_idempotency")).when(tx).executeWithoutResult(any());

        TransferService.Result result = service.transfer(ALICE, KEY,
                new TransferRequest(fromId, to.number(), new BigDecimal("9250.00"), null));

        assertThat(result.replayed()).isTrue();
        assertThat(result.operation()).isSameAs(winner);
        verifyNoInteractions(executor);
    }

    @Test
    void conversionGoesThroughRubWithoutLosingPrecision() {
        ExchangeRateService rates = new ExchangeRateService(properties);
        assertThat(rates.convert(new BigDecimal("9250.00"), "RUB", "USD")).isEqualByComparingTo("100.00");
        assertThat(rates.convert(new BigDecimal("100.00"), "USD", "EUR")).isEqualByComparingTo("92.32");
        assertThat(rates.rate("USD", "RUB")).isEqualByComparingTo("92.50");
    }

    private static TransferRequest request(UUID fromId, String amount) {
        return new TransferRequest(fromId, "40817810000000000009", new BigDecimal(amount), null);
    }

    private static Operation transfer(UUID fromId, String amount, OperationStatus status) {
        Operation op = new Operation();
        op.setId(UUID.randomUUID());
        op.setType(OperationType.TRANSFER);
        op.setStatus(status);
        op.setFromAccountId(fromId);
        op.setAmount(new BigDecimal(amount));
        return op;
    }
}
