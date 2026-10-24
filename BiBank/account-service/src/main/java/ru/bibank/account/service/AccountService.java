package ru.bibank.account.service;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.bibank.account.dto.*;
import ru.bibank.account.entity.*;
import ru.bibank.account.repository.AccountRepository;
import ru.bibank.common.events.EventEnvelope;
import ru.bibank.common.events.EventTypes;
import ru.bibank.common.events.Topics;
import ru.bibank.common.outbox.OutboxWriter;
import ru.bibank.common.security.AuthenticatedUser;
import ru.bibank.common.web.ApiException;
import ru.bibank.common.web.PageResponse;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class AccountService {

    static final int MAX_ACCOUNTS_PER_USER = 10;

    private final AccountRepository repository;
    private final AccountNumberGenerator numberGenerator;
    private final OutboxWriter outboxWriter;

    public AccountService(AccountRepository repository, AccountNumberGenerator numberGenerator,
                          OutboxWriter outboxWriter) {
        this.repository = repository;
        this.numberGenerator = numberGenerator;
        this.outboxWriter = outboxWriter;
    }

    @Transactional(readOnly = true)
    public List<AccountResponse> getAccounts(Long ownerId) {
        return repository.findAllByOwnerIdOrderByCreatedAt(ownerId).stream()
                .map(AccountResponse::from)
                .toList();
    }

    @Transactional
    public AccountResponse createAccount(AuthenticatedUser user, CreateAccountRequest request) {
        AccountType type = request.type() == null ? AccountType.MAIN : request.type();
        if (type == AccountType.SYSTEM) {
            throw ApiException.badRequest("INVALID_ACCOUNT_TYPE", "Недопустимый тип счёта");
        }
        if (repository.countByOwnerIdAndStatusNot(user.id(), AccountStatus.CLOSED) >= MAX_ACCOUNTS_PER_USER) {
            throw ApiException.unprocessable("ACCOUNT_LIMIT", "Можно открыть не более " + MAX_ACCOUNTS_PER_USER + " счетов");
        }

        Account account = new Account();
        account.setOwnerId(user.id());
        account.setOwnerName(user.name());
        account.setCurrency(request.currency());
        account.setType(type);
        account.setStatus(AccountStatus.ACTIVE);
        account.setName(request.name() == null || request.name().isBlank()
                ? defaultName(type, request.currency())
                : request.name().trim());
        account.setNumber(uniqueNumber(request.currency()));

        repository.save(account);

        outboxWriter.write(Topics.ACCOUNT_EVENTS, EventEnvelope.of(
                EventTypes.ACCOUNT_CREATED, "account-service", user.id(), account.getId(),
                Map.of("accountId", account.getId().toString(),
                        "number", account.getNumber(),
                        "currency", account.getCurrency().name(),
                        "type", account.getType().name())));

        return AccountResponse.from(account);
    }

    @Transactional(readOnly = true)
    public AccountResponse getAccount(AuthenticatedUser user, UUID id) {
        return AccountResponse.from(findAccessible(user, id));
    }

    @Transactional(readOnly = true)
    public BalanceResponse getBalance(AuthenticatedUser user, UUID id) {
        Account account = findAccessible(user, id);
        return new BalanceResponse(account.getId(), account.getBalance(), account.getCurrency().name(), Instant.now());
    }

    @Transactional(readOnly = true)
    public AccountLookupResponse lookup(String number) {
        Account account = repository.findByNumber(number.replaceAll("\\s", ""))
                .filter(a -> !a.isSystem())
                .orElseThrow(() -> ApiException.notFound("Счёт получателя не найден"));
        return new AccountLookupResponse(account.getNumber(), account.getCurrency().name(),
                maskName(account.getOwnerName()), account.getStatus() == AccountStatus.ACTIVE);
    }

    @Transactional
    public AccountResponse changeStatus(AuthenticatedUser user, UUID id, AccountStatus newStatus) {
        Account account = repository.lockAllByIds(List.of(id)).stream().findFirst()
                .orElseThrow(() -> ApiException.notFound("Счёт не найден"));
        if (account.isSystem() || (!user.isAdmin() && !account.getOwnerId().equals(user.id()))) {
            throw ApiException.notFound("Счёт не найден");
        }
        if (account.getStatus() == AccountStatus.CLOSED) {
            throw ApiException.unprocessable("ACCOUNT_CLOSED", "Закрытый счёт нельзя изменить");
        }
        if (newStatus == AccountStatus.CLOSED && account.getBalance().signum() != 0) {
            throw ApiException.unprocessable("NON_ZERO_BALANCE", "Перед закрытием переведите остаток со счёта");
        }
        if (account.getStatus() == newStatus) {
            return AccountResponse.from(account);
        }
        AccountStatus old = account.getStatus();
        account.setStatus(newStatus);

        outboxWriter.write(Topics.ACCOUNT_EVENTS, EventEnvelope.of(
                EventTypes.ACCOUNT_STATUS_CHANGED, "account-service", account.getOwnerId(), account.getId(),
                Map.of("accountId", account.getId().toString(),
                        "number", account.getNumber(),
                        "oldStatus", old.name(),
                        "newStatus", newStatus.name())));
        return AccountResponse.from(account);
    }

    @Transactional(readOnly = true)
    public PageResponse<AccountResponse> search(Long ownerId, AccountStatus status, int page, int size) {
        var pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        return PageResponse.of(repository.search(ownerId, status, pageable), AccountResponse::from);
    }

    @Transactional(readOnly = true)
    public AccountStatsResponse stats() {
        List<AccountStatsResponse.CurrencyStats> byCurrency = repository.statsByCurrency().stream()
                .map(r -> new AccountStatsResponse.CurrencyStats(((Currency) r[0]).name(),
                        ((Number) r[1]).longValue(), (BigDecimal) r[2]))
                .toList();
        long total = byCurrency.stream().mapToLong(AccountStatsResponse.CurrencyStats::accounts).sum();
        return new AccountStatsResponse(total, byCurrency);
    }

    // ---- internal API ----

    @Transactional(readOnly = true)
    public InternalAccountResponse internalById(UUID id) {
        return repository.findById(id).map(InternalAccountResponse::from)
                .orElseThrow(() -> ApiException.notFound("Счёт не найден"));
    }

    @Transactional(readOnly = true)
    public InternalAccountResponse internalByNumber(String number) {
        return repository.findByNumber(number).filter(a -> !a.isSystem()).map(InternalAccountResponse::from)
                .orElseThrow(() -> ApiException.notFound("Счёт получателя не найден"));
    }

    @Transactional(readOnly = true)
    public InternalAccountResponse systemAccount(Currency currency) {
        return repository.findByTypeAndCurrency(AccountType.SYSTEM, currency).map(InternalAccountResponse::from)
                .orElseThrow(() -> new IllegalStateException("System account for " + currency + " is missing"));
    }

    /**
     * Чужой счёт для обычного пользователя неотличим от несуществующего (404),
     * чтобы не раскрывать идентификаторы.
     */
    private Account findAccessible(AuthenticatedUser user, UUID id) {
        Account account = repository.findById(id)
                .filter(a -> !a.isSystem())
                .orElseThrow(() -> ApiException.notFound("Счёт не найден"));
        if (!user.isAdmin() && !account.getOwnerId().equals(user.id())) {
            throw ApiException.notFound("Счёт не найден");
        }
        return account;
    }

    private String uniqueNumber(Currency currency) {
        for (int i = 0; i < 10; i++) {
            String number = numberGenerator.generate(currency);
            if (!repository.existsByNumber(number)) {
                return number;
            }
        }
        throw new IllegalStateException("Cannot generate unique account number");
    }

    private static String defaultName(AccountType type, Currency currency) {
        return (type == AccountType.SAVINGS ? "Накопительный" : "Основной") + " " + currency.name();
    }

    static String maskName(String fullName) {
        if (fullName == null || fullName.isBlank()) {
            return "Клиент BiBank";
        }
        String[] parts = fullName.trim().split("\\s+");
        if (parts.length == 1) {
            return parts[0];
        }
        return parts[0] + " " + parts[1].charAt(0) + ".";
    }
}
