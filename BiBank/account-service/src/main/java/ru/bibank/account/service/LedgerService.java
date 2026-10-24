package ru.bibank.account.service;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.bibank.account.dto.OperationRequest;
import ru.bibank.account.dto.OperationResult;
import jakarta.persistence.EntityManager;
import ru.bibank.account.entity.Account;
import ru.bibank.account.entity.AccountStatus;
import ru.bibank.account.entity.AppliedOperation;
import ru.bibank.account.entity.Currency;
import ru.bibank.account.entity.LedgerEntry;
import ru.bibank.account.repository.AccountRepository;
import ru.bibank.account.repository.AppliedOperationRepository;
import ru.bibank.account.repository.LedgerEntryRepository;
import ru.bibank.common.web.ApiException;

import java.math.BigDecimal;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Единственная точка изменения балансов.
 * <p>
 * Все проводки операции применяются в одной транзакции PostgreSQL под пессимистичной
 * блокировкой затронутых счетов. Повтор операции с тем же operationId возвращает
 * ранее сохранённый результат и не меняет балансы.
 */
@Service
public class LedgerService {

    static final int MONEY_SCALE = 2;

    private final AccountRepository accountRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final AppliedOperationRepository appliedOperationRepository;
    private final EntityManager entityManager;

    public LedgerService(AccountRepository accountRepository,
                         LedgerEntryRepository ledgerEntryRepository,
                         AppliedOperationRepository appliedOperationRepository,
                         EntityManager entityManager) {
        this.accountRepository = accountRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.appliedOperationRepository = appliedOperationRepository;
        this.entityManager = entityManager;
    }

    @Transactional
    public OperationResult apply(OperationRequest request) {
        validateShape(request);

        Set<UUID> ids = request.postings().stream()
                .map(OperationRequest.Posting::accountId)
                .collect(Collectors.toCollection(TreeSet::new));

        List<Account> locked = accountRepository.lockAllByIds(ids);
        if (locked.size() != ids.size()) {
            throw ApiException.notFound("Счёт не найден");
        }
        Map<UUID, Account> accounts = locked.stream()
                .collect(Collectors.toMap(Account::getId, Function.identity()));

        // Проверка идемпотентности после захвата блокировок: параллельный дубликат
        // дождётся завершения первой транзакции и увидит её результат.
        if (appliedOperationRepository.existsById(request.operationId())) {
            return previousResult(request.operationId());
        }

        validateBusinessRules(request, accounts);

        try {
            entityManager.persist(new AppliedOperation(request.operationId(), request.type()));
            entityManager.flush();
        } catch (DataIntegrityViolationException | jakarta.persistence.PersistenceException e) {
            throw ApiException.conflict("DUPLICATE_OPERATION", "Операция уже выполняется");
        }

        List<OperationResult.AppliedPosting> applied = new ArrayList<>();
        for (OperationRequest.Posting posting : request.postings()) {
            Account account = accounts.get(posting.accountId());
            BigDecimal amount = posting.amount().setScale(MONEY_SCALE);
            account.applyPosting(amount);
            ledgerEntryRepository.save(new LedgerEntry(request.operationId(), account.getId(), amount, account.getBalance()));
            applied.add(new OperationResult.AppliedPosting(account.getId(), amount, account.getBalance()));
        }

        accountRepository.flush();

        return new OperationResult(request.operationId(), false, applied);
    }

    private void validateShape(OperationRequest request) {
        for (OperationRequest.Posting p : request.postings()) {
            if (p.amount().signum() == 0) {
                throw ApiException.badRequest("INVALID_AMOUNT", "Сумма проводки не может быть нулевой");
            }
            if (p.amount().stripTrailingZeros().scale() > MONEY_SCALE) {
                throw ApiException.badRequest("INVALID_AMOUNT", "Сумма должна иметь не более двух знаков после запятой");
            }
        }
        long distinct = request.postings().stream().map(OperationRequest.Posting::accountId).distinct().count();
        if (distinct != request.postings().size()) {
            throw ApiException.badRequest("INVALID_OPERATION", "Счёт не может участвовать в операции дважды");
        }
    }

    private void validateBusinessRules(OperationRequest request, Map<UUID, Account> accounts) {
        Map<Currency, BigDecimal> sumByCurrency = new EnumMap<>(Currency.class);

        for (OperationRequest.Posting posting : request.postings()) {
            Account account = accounts.get(posting.accountId());

            if (posting.expectedOwnerId() != null && !posting.expectedOwnerId().equals(account.getOwnerId())) {
                throw ApiException.forbidden("Счёт не принадлежит пользователю");
            }
            if (account.getStatus() != AccountStatus.ACTIVE) {
                throw ApiException.unprocessable("ACCOUNT_NOT_ACTIVE",
                        "Счёт " + account.getNumber() + " недоступен для операций (статус " + account.getStatus() + ")");
            }
            if (posting.amount().signum() < 0 && !account.isSystem()
                    && account.getBalance().add(posting.amount()).signum() < 0) {
                throw ApiException.unprocessable("INSUFFICIENT_FUNDS", "Недостаточно средств на счёте");
            }
            sumByCurrency.merge(account.getCurrency(), posting.amount(), BigDecimal::add);
        }

        boolean balanced = sumByCurrency.values().stream().allMatch(v -> v.signum() == 0);
        if (!balanced) {
            throw ApiException.badRequest("UNBALANCED_OPERATION",
                    "Сумма проводок по каждой валюте должна быть равна нулю");
        }
    }

    private OperationResult previousResult(UUID operationId) {
        List<OperationResult.AppliedPosting> postings = ledgerEntryRepository.findAllByOperationId(operationId).stream()
                .map(e -> new OperationResult.AppliedPosting(e.getAccountId(), e.getAmount(), e.getBalanceAfter()))
                .toList();
        return new OperationResult(operationId, true, postings);
    }
}
