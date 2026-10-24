package ru.bibank.payment.service;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import ru.bibank.common.security.AuthenticatedUser;
import ru.bibank.common.web.ApiException;
import ru.bibank.payment.client.AccountClient;
import ru.bibank.payment.client.AccountInfo;
import ru.bibank.payment.config.PaymentProperties;
import ru.bibank.payment.dto.InternalOperationRequest;
import ru.bibank.payment.dto.TopUpRequest;
import ru.bibank.payment.dto.TransferPreview;
import ru.bibank.payment.dto.TransferRequest;
import ru.bibank.payment.entity.Operation;
import ru.bibank.payment.entity.OperationStatus;
import ru.bibank.payment.entity.OperationType;
import ru.bibank.payment.repository.OperationRepository;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Создание финансовых операций с защитой от повторного выполнения.
 * <p>
 * Клиент передаёт заголовок Idempotency-Key. Пара (пользователь, ключ) уникальна в БД,
 * поэтому повторная отправка того же запроса возвращает уже созданную операцию,
 * а не списывает деньги второй раз.
 */
@Service
public class TransferService {

    public record Result(Operation operation, boolean replayed) {
    }

    private final OperationRepository repository;
    private final AccountClient accountClient;
    private final OperationExecutor executor;
    private final ExchangeRateService rates;
    private final PaymentProperties properties;
    private final TransactionTemplate tx;

    public TransferService(OperationRepository repository, AccountClient accountClient, OperationExecutor executor,
                           ExchangeRateService rates, PaymentProperties properties, TransactionTemplate tx) {
        this.repository = repository;
        this.accountClient = accountClient;
        this.executor = executor;
        this.rates = rates;
        this.properties = properties;
        this.tx = tx;
    }

    public TransferPreview preview(AuthenticatedUser user, TransferRequest request) {
        AccountInfo from = ownedActiveAccount(user, request.fromAccountId());
        AccountInfo to = recipient(request.toAccountNumber(), from);
        validateAmount(request.amount(), properties.maxTransferAmount());

        BigDecimal credited = rates.convert(request.amount(), from.currency(), to.currency());
        return new TransferPreview(from.id(), from.number(), to.number(), mask(to.ownerName()),
                request.amount(), from.currency(), credited, to.currency(),
                rates.rate(from.currency(), to.currency()), BigDecimal.ZERO,
                from.balance(), from.balance().compareTo(request.amount()) >= 0);
    }

    public Result transfer(AuthenticatedUser user, String idempotencyKey, TransferRequest request) {
        String key = normalizeKey(idempotencyKey);
        Optional<Operation> existing = repository.findByInitiatorIdAndIdempotencyKey(user.id(), key);
        if (existing.isPresent()) {
            return replay(existing.get(), OperationType.TRANSFER, request.fromAccountId(), request.amount());
        }

        validateAmount(request.amount(), properties.maxTransferAmount());
        AccountInfo from = ownedActiveAccount(user, request.fromAccountId());
        AccountInfo to = recipient(request.toAccountNumber(), from);

        Operation op = new Operation();
        op.setType(OperationType.TRANSFER);
        op.setStatus(OperationStatus.PENDING);
        op.setInitiatorId(user.id());
        op.setIdempotencyKey(key);
        op.setFromAccountId(from.id());
        op.setFromAccountNumber(from.number());
        op.setFromOwnerId(from.ownerId());
        op.setFromOwnerName(user.name());
        op.setToAccountId(to.id());
        op.setToAccountNumber(to.number());
        op.setToOwnerId(to.ownerId());
        op.setToOwnerName(mask(to.ownerName()));
        op.setAmount(request.amount());
        op.setCurrency(from.currency());
        op.setCreditedCurrency(to.currency());
        op.setCreditedAmount(rates.convert(request.amount(), from.currency(), to.currency()));
        op.setExchangeRate(from.currency().equals(to.currency()) ? null : rates.rate(from.currency(), to.currency()));
        op.setDescription(blankToNull(request.description()));

        return persistAndExecute(op, user.id(), key, request.fromAccountId(), request.amount());
    }

    public Result topUp(AuthenticatedUser user, String idempotencyKey, TopUpRequest request) {
        String key = normalizeKey(idempotencyKey);
        Optional<Operation> existing = repository.findByInitiatorIdAndIdempotencyKey(user.id(), key);
        if (existing.isPresent()) {
            return replay(existing.get(), OperationType.TOP_UP, request.accountId(), request.amount());
        }
        validateAmount(request.amount(), properties.maxTopUpAmount());
        AccountInfo account = ownedActiveAccount(user, request.accountId());

        Operation op = new Operation();
        op.setType(OperationType.TOP_UP);
        op.setStatus(OperationStatus.PENDING);
        op.setInitiatorId(user.id());
        op.setIdempotencyKey(key);
        op.setFromOwnerId(0L);
        op.setFromOwnerName("BiBank");
        op.setToAccountId(account.id());
        op.setToAccountNumber(account.number());
        op.setToOwnerId(account.ownerId());
        op.setToOwnerName("BiBank");
        op.setAmount(request.amount());
        op.setCurrency(account.currency());
        op.setCreditedAmount(request.amount());
        op.setCreditedCurrency(account.currency());
        op.setDescription("Демо-пополнение счёта");

        return persistAndExecute(op, user.id(), key, request.accountId(), request.amount());
    }

    /**
     * Операция по запросу Loan Service. Идемпотентна по operationId.
     */
    public Operation internal(InternalOperationRequest request) {
        Optional<Operation> existing = repository.findById(request.operationId());
        if (existing.isPresent()) {
            return executor.execute(existing.get().getId());
        }
        if (request.fromAccountId() == null && request.toAccountId() == null) {
            throw ApiException.badRequest("INVALID_OPERATION", "Должен быть указан хотя бы один клиентский счёт");
        }
        AccountInfo from = request.fromAccountId() == null ? null : accountClient.getById(request.fromAccountId());
        AccountInfo to = request.toAccountId() == null ? null : accountClient.getById(request.toAccountId());
        AccountInfo client = from != null ? from : to;
        if (!client.ownerId().equals(request.userId())) {
            throw ApiException.forbidden("Счёт не принадлежит пользователю");
        }
        if (from != null && to != null && !from.currency().equals(to.currency())) {
            throw ApiException.badRequest("CURRENCY_MISMATCH", "Валюты счетов должны совпадать");
        }

        Operation op = new Operation();
        op.setId(request.operationId());
        op.setType(request.type());
        op.setStatus(OperationStatus.PENDING);
        op.setInitiatorId(request.userId());
        op.setFromAccountId(from == null ? null : from.id());
        op.setFromAccountNumber(from == null ? null : from.number());
        op.setFromOwnerId(from == null ? 0L : from.ownerId());
        op.setFromOwnerName(from == null ? "BiBank" : from.ownerName());
        op.setToAccountId(to == null ? null : to.id());
        op.setToAccountNumber(to == null ? null : to.number());
        op.setToOwnerId(to == null ? 0L : to.ownerId());
        op.setToOwnerName("BiBank");
        op.setAmount(request.amount());
        op.setCurrency(client.currency());
        op.setCreditedAmount(request.amount());
        op.setCreditedCurrency(client.currency());
        op.setDescription(blankToNull(request.description()));
        op.setExternalRef(request.externalRef());

        try {
            tx.executeWithoutResult(s -> repository.saveAndFlush(op));
        } catch (DataIntegrityViolationException e) {
            // параллельный повтор того же operationId
        }
        return executor.execute(request.operationId());
    }

    private Result persistAndExecute(Operation op, Long userId, String key, UUID accountId, BigDecimal amount) {
        try {
            tx.executeWithoutResult(s -> repository.saveAndFlush(op));
        } catch (DataIntegrityViolationException e) {
            // Параллельный запрос с тем же ключом успел создать операцию первым
            Operation winner = repository.findByInitiatorIdAndIdempotencyKey(userId, key).orElseThrow(() -> e);
            return replay(winner, op.getType(), accountId, amount);
        }
        return new Result(executor.execute(op.getId()), false);
    }

    private Result replay(Operation existing, OperationType type, UUID accountId, BigDecimal amount) {
        UUID existingAccount = type == OperationType.TOP_UP ? existing.getToAccountId() : existing.getFromAccountId();
        if (existing.getType() != type || !Objects.equals(existingAccount, accountId)
                || existing.getAmount().compareTo(amount) != 0) {
            throw ApiException.unprocessable("IDEMPOTENCY_KEY_REUSED",
                    "Ключ идемпотентности уже использован для другой операции");
        }
        Operation current = existing.getStatus() == OperationStatus.PENDING
                ? executor.execute(existing.getId())
                : existing;
        return new Result(current, true);
    }

    private AccountInfo ownedActiveAccount(AuthenticatedUser user, UUID accountId) {
        AccountInfo account;
        try {
            account = accountClient.getById(accountId);
        } catch (ApiException e) {
            if (e.getStatus().value() == 404) {
                throw ApiException.notFound("Счёт списания не найден");
            }
            throw e;
        }
        if (account.isSystem() || !account.ownerId().equals(user.id())) {
            // чужой счёт неотличим от несуществующего
            throw ApiException.notFound("Счёт списания не найден");
        }
        if (!account.isActive()) {
            throw ApiException.unprocessable("ACCOUNT_NOT_ACTIVE", "Счёт списания недоступен для операций");
        }
        return account;
    }

    private AccountInfo recipient(String number, AccountInfo from) {
        AccountInfo to = accountClient.getByNumber(number);
        if (to.id().equals(from.id())) {
            throw ApiException.badRequest("SAME_ACCOUNT", "Счёт списания и зачисления совпадают");
        }
        if (!to.isActive()) {
            throw ApiException.unprocessable("RECIPIENT_NOT_ACTIVE", "Счёт получателя недоступен для зачисления");
        }
        return to;
    }

    private static void validateAmount(BigDecimal amount, BigDecimal max) {
        if (amount == null || amount.signum() <= 0) {
            throw ApiException.badRequest("INVALID_AMOUNT", "Сумма должна быть больше нуля");
        }
        if (amount.stripTrailingZeros().scale() > 2) {
            throw ApiException.badRequest("INVALID_AMOUNT", "Не более двух знаков после запятой");
        }
        if (amount.compareTo(max) > 0) {
            throw ApiException.unprocessable("AMOUNT_LIMIT_EXCEEDED", "Превышен лимит одной операции: " + max.toPlainString());
        }
    }

    static String normalizeKey(String key) {
        if (key == null || key.isBlank()) {
            throw ApiException.badRequest("IDEMPOTENCY_KEY_REQUIRED", "Требуется заголовок Idempotency-Key");
        }
        String trimmed = key.trim();
        if (trimmed.length() > 100) {
            throw ApiException.badRequest("INVALID_IDEMPOTENCY_KEY", "Idempotency-Key не длиннее 100 символов");
        }
        return trimmed;
    }

    static String mask(String fullName) {
        if (fullName == null || fullName.isBlank()) {
            return "Клиент BiBank";
        }
        String[] parts = fullName.trim().split("\\s+");
        return parts.length == 1 ? parts[0] : parts[0] + " " + parts[1].charAt(0) + ".";
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
