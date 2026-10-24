package ru.bibank.account;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import ru.bibank.account.dto.AccountResponse;
import ru.bibank.account.dto.CreateAccountRequest;
import ru.bibank.account.dto.OperationRequest;
import ru.bibank.account.dto.OperationRequest.Posting;
import ru.bibank.account.dto.OperationResult;
import ru.bibank.account.entity.AccountType;
import ru.bibank.account.entity.Currency;
import ru.bibank.account.repository.AccountRepository;
import ru.bibank.account.service.AccountService;
import ru.bibank.account.service.LedgerService;
import ru.bibank.common.outbox.OutboxPublisher;
import ru.bibank.common.security.AuthenticatedUser;
import ru.bibank.common.web.ApiException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Проверка денежного ядра на настоящем PostgreSQL: блокировки, идемпотентность,
 * двойная запись и запрет отрицательного баланса при конкурентных списаниях.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
        "bibank.security.jwt-secret=test-secret-test-secret-test-secret-123456",
        "bibank.security.internal-token=test-internal-token",
        "spring.kafka.admin.auto-create=false"
})
class LedgerServiceIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    /** Публикация в Kafka в этом тесте не нужна: проверяем только запись в outbox. */
    @MockitoBean
    OutboxPublisher outboxPublisher;

    @Autowired
    LedgerService ledger;
    @Autowired
    AccountService accounts;
    @Autowired
    AccountRepository repository;
    @Autowired
    JdbcTemplate jdbc;

    private AuthenticatedUser alice;
    private AuthenticatedUser bob;

    @BeforeEach
    void users() {
        long seed = System.nanoTime() % 1_000_000;
        alice = new AuthenticatedUser(seed, "alice@test", "ROLE_USER", "Алиса Иванова");
        bob = new AuthenticatedUser(seed + 1, "bob@test", "ROLE_USER", "Борис Петров");
    }

    @Test
    void topUpAndTransferKeepDoubleEntryBalance() {
        AccountResponse a = open(alice, Currency.RUB);
        AccountResponse b = open(bob, Currency.RUB);
        UUID treasury = systemAccount(Currency.RUB);

        apply(UUID.randomUUID(), posting(treasury, "-1000.00"), posting(a.id(), "1000.00"));
        apply(UUID.randomUUID(), posting(a.id(), "-250.50", alice.id()), posting(b.id(), "250.50"));

        assertThat(balance(a.id())).isEqualByComparingTo("749.50");
        assertThat(balance(b.id())).isEqualByComparingTo("250.50");
        BigDecimal ledgerSum = jdbc.queryForObject(
                "select coalesce(sum(amount), 0) from ledger_entries where account_id in (?, ?)",
                BigDecimal.class, a.id(), b.id());
        assertThat(ledgerSum).isEqualByComparingTo("1000.00");
    }

    @Test
    void sameOperationIdIsAppliedOnlyOnce() {
        AccountResponse a = open(alice, Currency.RUB);
        UUID treasury = systemAccount(Currency.RUB);
        UUID operationId = UUID.randomUUID();

        OperationResult first = apply(operationId, posting(treasury, "-500.00"), posting(a.id(), "500.00"));
        OperationResult second = apply(operationId, posting(treasury, "-500.00"), posting(a.id(), "500.00"));

        assertThat(first.alreadyApplied()).isFalse();
        assertThat(second.alreadyApplied()).isTrue();
        assertThat(balance(a.id())).isEqualByComparingTo("500.00");
    }

    @Test
    void rejectsInsufficientFundsUnbalancedAndForeignAccount() {
        AccountResponse a = open(alice, Currency.RUB);
        AccountResponse b = open(bob, Currency.RUB);
        UUID treasury = systemAccount(Currency.RUB);
        apply(UUID.randomUUID(), posting(treasury, "-100.00"), posting(a.id(), "100.00"));

        assertThatThrownBy(() -> apply(UUID.randomUUID(), posting(a.id(), "-100.01"), posting(b.id(), "100.01")))
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo("INSUFFICIENT_FUNDS");
        assertThatThrownBy(() -> apply(UUID.randomUUID(), posting(a.id(), "-10.00"), posting(b.id(), "9.00")))
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo("UNBALANCED_OPERATION");
        assertThatThrownBy(() -> apply(UUID.randomUUID(), posting(a.id(), "-10.00", bob.id()), posting(b.id(), "10.00")))
                .isInstanceOf(ApiException.class);

        assertThat(balance(a.id())).isEqualByComparingTo("100.00");
        assertThat(balance(b.id())).isEqualByComparingTo("0.00");
    }

    @Test
    void parallelDebitsNeverOverdraw() throws Exception {
        AccountResponse a = open(alice, Currency.RUB);
        AccountResponse b = open(bob, Currency.RUB);
        UUID treasury = systemAccount(Currency.RUB);
        apply(UUID.randomUUID(), posting(treasury, "-1000.00"), posting(a.id(), "1000.00"));

        int threads = 20;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            results.add(pool.submit(() -> {
                start.await();
                try {
                    // Все потоки стартуют одновременно и конкурируют за блокировку одного счёта.
                    apply(UUID.randomUUID(), posting(a.id(), "-100.00"), posting(b.id(), "100.00"));
                    return true;
                } catch (ApiException e) {
                    return false;
                }
            }));
        }
        start.countDown();
        int succeeded = 0;
        for (Future<Boolean> f : results) {
            if (f.get(30, TimeUnit.SECONDS)) {
                succeeded++;
            }
        }
        pool.shutdown();

        assertThat(succeeded).isEqualTo(10);
        assertThat(balance(a.id())).isEqualByComparingTo("0.00");
        assertThat(balance(b.id())).isEqualByComparingTo("1000.00");
    }

    @Test
    void accountCreationWritesOutboxEventInSameTransaction() {
        AccountResponse a = open(alice, Currency.USD);
        Integer events = jdbc.queryForObject(
                "select count(*) from outbox_events where payload like ?", Integer.class, "%" + a.id() + "%");
        assertThat(events).isEqualTo(1);
        assertThat(a.number()).hasSize(20).startsWith("40817840");
    }

    private AccountResponse open(AuthenticatedUser user, Currency currency) {
        return accounts.createAccount(user, new CreateAccountRequest(currency, AccountType.MAIN, null));
    }

    private UUID systemAccount(Currency currency) {
        return accounts.systemAccount(currency).id();
    }

    private BigDecimal balance(UUID id) {
        return repository.findById(id).orElseThrow().getBalance();
    }

    private OperationResult apply(UUID operationId, Posting... postings) {
        return ledger.apply(new OperationRequest(operationId, "TEST", List.of(postings)));
    }

    private static Posting posting(UUID account, String amount) {
        return new Posting(account, new BigDecimal(amount), null);
    }

    private static Posting posting(UUID account, String amount, Long owner) {
        return new Posting(account, new BigDecimal(amount), owner);
    }
}
