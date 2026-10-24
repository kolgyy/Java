package ru.bibank.loan.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import ru.bibank.common.events.EventEnvelope;
import ru.bibank.common.events.EventTypes;
import ru.bibank.common.events.Topics;
import ru.bibank.common.outbox.OutboxWriter;
import ru.bibank.common.security.AuthenticatedUser;
import ru.bibank.common.web.ApiException;
import ru.bibank.loan.client.BankingClient;
import ru.bibank.loan.dto.DepositResponse;
import ru.bibank.loan.dto.OpenDepositRequest;
import ru.bibank.loan.entity.Deposit;
import ru.bibank.loan.entity.DepositProduct;
import ru.bibank.loan.entity.DepositStatus;
import ru.bibank.loan.entity.InterestAccrual;
import ru.bibank.loan.repository.DepositRepository;
import ru.bibank.loan.repository.InterestAccrualRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Вклады: открытие, ежедневное начисление процентов, закрытие в срок и досрочно.
 * <p>
 * Правила:
 * <ul>
 *     <li>проценты начисляются ежедневно (база 365 дней), с капитализацией — на сумму вклада с процентами;</li>
 *     <li>при закрытии в дату окончания выплачивается вклад и все начисленные проценты;</li>
 *     <li>при досрочном закрытии проценты пересчитываются по ставке досрочного закрытия (простые проценты).</li>
 * </ul>
 */
@Service
public class DepositService {

    private static final Logger log = LoggerFactory.getLogger(DepositService.class);
    static final ZoneId ZONE = ZoneId.of("Europe/Moscow");

    private final DepositRepository deposits;
    private final InterestAccrualRepository accruals;
    private final ProductService productService;
    private final BankingClient bankingClient;
    private final OutboxWriter outbox;
    private final TransactionTemplate tx;

    public DepositService(DepositRepository deposits, InterestAccrualRepository accruals,
                          ProductService productService, BankingClient bankingClient,
                          OutboxWriter outbox, TransactionTemplate tx) {
        this.deposits = deposits;
        this.accruals = accruals;
        this.productService = productService;
        this.bankingClient = bankingClient;
        this.outbox = outbox;
        this.tx = tx;
    }

    public DepositResponse open(AuthenticatedUser user, OpenDepositRequest request) {
        DepositProduct product = productService.activeDepositProduct(request.productId());
        if (request.amount().compareTo(product.getMinAmount()) < 0 || request.amount().compareTo(product.getMaxAmount()) > 0) {
            throw ApiException.unprocessable("AMOUNT_OUT_OF_RANGE", "Сумма вклада должна быть от "
                    + product.getMinAmount().toPlainString() + " до " + product.getMaxAmount().toPlainString());
        }
        if (request.termMonths() < product.getMinTermMonths() || request.termMonths() > product.getMaxTermMonths()) {
            throw ApiException.unprocessable("TERM_OUT_OF_RANGE", "Срок вклада должен быть от "
                    + product.getMinTermMonths() + " до " + product.getMaxTermMonths() + " месяцев");
        }
        bankingClient.ownedActiveAccount(user.id(), request.accountId(), product.getCurrency());

        LocalDate today = today();
        UUID id = tx.execute(s -> {
            Deposit d = new Deposit();
            d.setUserId(user.id());
            d.setProductId(product.getId());
            d.setProductName(product.getName());
            d.setAccountId(request.accountId());
            d.setCurrency(product.getCurrency());
            d.setPrincipal(request.amount());
            d.setAnnualRate(product.getAnnualRate());
            d.setEarlyCloseRate(product.getEarlyCloseRate());
            d.setCapitalization(product.isCapitalization());
            d.setTermMonths(request.termMonths());
            d.setOpenDate(today);
            d.setMaturityDate(today.plusMonths(request.termMonths()));
            d.setAccruedThrough(today);
            d.setStatus(DepositStatus.OPENING);
            d.setOpenOperationId(UUID.randomUUID());
            deposits.save(d);
            return d.getId();
        });

        Deposit result = processOpening(id);
        if (result.getStatus() == DepositStatus.FAILED) {
            throw ApiException.unprocessable("DEPOSIT_OPEN_FAILED", result.getFailureReason());
        }
        return toResponse(result, List.of());
    }

    public Deposit processOpening(UUID id) {
        Deposit d = deposits.findById(id).orElseThrow();
        if (d.getStatus() != DepositStatus.OPENING) {
            return d;
        }
        var outcome = bankingClient.execute(d.getOpenOperationId(), BankingClient.OperationType.DEPOSIT_OPEN,
                d.getUserId(), d.getAccountId(), null, d.getPrincipal(),
                "Открытие вклада «" + d.getProductName() + "»", d.getId().toString());
        if (outcome.completed()) {
            return tx.execute(s -> {
                Deposit dep = deposits.findById(id).orElseThrow();
                if (dep.getStatus() == DepositStatus.OPENING) {
                    dep.setStatus(DepositStatus.ACTIVE);
                    outbox.write(Topics.LOAN_EVENTS, EventEnvelope.of(EventTypes.DEPOSIT_OPENED, "loan-service",
                            dep.getUserId(), dep.getId(), Map.of("productName", dep.getProductName(),
                                    "amount", dep.getPrincipal(), "currency", dep.getCurrency(),
                                    "rate", dep.getAnnualRate().stripTrailingZeros().toPlainString(),
                                    "maturityDate", dep.getMaturityDate().toString())));
                }
                return dep;
            });
        }
        if (outcome.failed()) {
            return tx.execute(s -> {
                Deposit dep = deposits.findById(id).orElseThrow();
                if (dep.getStatus() == DepositStatus.OPENING) {
                    dep.setStatus(DepositStatus.FAILED);
                    dep.setFailureReason(outcome.failureReason());
                }
                return dep;
            });
        }
        return d;
    }

    public DepositResponse close(AuthenticatedUser user, UUID id) {
        tx.executeWithoutResult(s -> {
            Deposit d = owned(user, id);
            if (d.getStatus() != DepositStatus.ACTIVE) {
                throw ApiException.unprocessable("DEPOSIT_NOT_ACTIVE", "Вклад не активен");
            }
            prepareClose(d, today());
        });
        Deposit result = processClosing(id);
        if (result.getStatus() == DepositStatus.ACTIVE) {
            throw ApiException.unprocessable("DEPOSIT_CLOSE_FAILED",
                    "Не удалось закрыть вклад: " + result.getFailureReason());
        }
        return toResponse(result, List.of());
    }

    /**
     * Фиксирует сумму выплаты и переводит вклад в CLOSING (в той же транзакции, что и проверка статуса).
     */
    void prepareClose(Deposit d, LocalDate closeDate) {
        boolean early = closeDate.isBefore(d.getMaturityDate());
        BigDecimal interest = early
                ? InterestCalculator.simple(d.getPrincipal(), d.getEarlyCloseRate(), d.getOpenDate(), closeDate)
                : d.getAccruedInterest();
        d.setEarlyClosed(early);
        d.setPayoutInterest(interest);
        d.setPayoutAmount(d.getPrincipal().add(interest));
        d.setCloseOperationId(UUID.randomUUID());
        d.setFailureReason(null);
        d.setStatus(DepositStatus.CLOSING);
    }

    public Deposit processClosing(UUID id) {
        Deposit d = deposits.findById(id).orElseThrow();
        if (d.getStatus() != DepositStatus.CLOSING) {
            return d;
        }
        var outcome = bankingClient.execute(d.getCloseOperationId(), BankingClient.OperationType.DEPOSIT_CLOSE,
                d.getUserId(), null, d.getAccountId(), d.getPayoutAmount(),
                "Закрытие вклада «" + d.getProductName() + "»", d.getId().toString());
        if (outcome.completed()) {
            return tx.execute(s -> {
                Deposit dep = deposits.findById(id).orElseThrow();
                if (dep.getStatus() == DepositStatus.CLOSING) {
                    dep.setStatus(DepositStatus.CLOSED);
                    dep.setClosedAt(Instant.now());
                    outbox.write(Topics.LOAN_EVENTS, EventEnvelope.of(EventTypes.DEPOSIT_CLOSED, "loan-service",
                            dep.getUserId(), dep.getId(), Map.of("productName", dep.getProductName(),
                                    "payout", dep.getPayoutAmount(), "interest", dep.getPayoutInterest(),
                                    "currency", dep.getCurrency(), "early", dep.isEarlyClosed())));
                }
                return dep;
            });
        }
        if (outcome.failed()) {
            // Компенсация: выплата не прошла — вклад остаётся активным
            return tx.execute(s -> {
                Deposit dep = deposits.findById(id).orElseThrow();
                if (dep.getStatus() == DepositStatus.CLOSING) {
                    dep.setStatus(DepositStatus.ACTIVE);
                    dep.setFailureReason(outcome.failureReason());
                    dep.setCloseOperationId(null);
                    dep.setPayoutAmount(null);
                    dep.setPayoutInterest(null);
                    dep.setEarlyClosed(false);
                }
                return dep;
            });
        }
        return d;
    }

    /**
     * Начисляет проценты за дни (accruedThrough; until], не позже даты окончания вклада.
     * Каждый день — отдельная запись; уникальность (вклад, дата) исключает двойное начисление.
     *
     * @return true, если вклад достиг даты окончания
     */
    public boolean accrue(UUID depositId, LocalDate until) {
        Boolean matured = tx.execute(s -> {
            Deposit d = deposits.findById(depositId).orElseThrow();
            if (d.getStatus() != DepositStatus.ACTIVE) {
                return false;
            }
            LocalDate end = until.isAfter(d.getMaturityDate()) ? d.getMaturityDate() : until;
            for (LocalDate day = d.getAccruedThrough().plusDays(1); !day.isAfter(end); day = day.plusDays(1)) {
                BigDecimal base = d.isCapitalization() ? d.getPrincipal().add(d.getAccruedInterest()) : d.getPrincipal();
                BigDecimal interest = InterestCalculator.daily(base, d.getAnnualRate());
                accruals.save(new InterestAccrual(d.getId(), day, base, interest));
                d.setAccruedInterest(d.getAccruedInterest().add(interest));
                d.setAccruedThrough(day);
            }
            if (!d.getAccruedThrough().isBefore(d.getMaturityDate())) {
                prepareClose(d, d.getMaturityDate());
                return true;
            }
            return false;
        });
        if (Boolean.TRUE.equals(matured)) {
            Deposit closed = processClosing(depositId);
            log.info("Deposit {} matured → {}", depositId, closed.getStatus());
        }
        return Boolean.TRUE.equals(matured);
    }

    @Transactional(readOnly = true)
    public List<DepositResponse> mine(Long userId) {
        return deposits.findAllByUserIdOrderByCreatedAtDesc(userId).stream()
                .filter(d -> d.getStatus() != DepositStatus.FAILED)
                .map(d -> toResponse(d, null))
                .toList();
    }

    @Transactional(readOnly = true)
    public DepositResponse get(AuthenticatedUser user, UUID id) {
        Deposit d = owned(user, id);
        var list = accruals.findTop100ByDepositIdOrderByAccrualDateDesc(id).stream()
                .map(a -> new DepositResponse.AccrualResponse(a.getAccrualDate(), a.getBase(), a.getAmount()))
                .toList();
        return toResponse(d, list);
    }

    private DepositResponse toResponse(Deposit d, List<DepositResponse.AccrualResponse> list) {
        BigDecimal projected = InterestCalculator.projected(d.getPrincipal(), d.getAnnualRate(), d.getTermMonths(),
                d.isCapitalization(), d.getOpenDate());
        return DepositResponse.from(d, projected, list);
    }

    private Deposit owned(AuthenticatedUser user, UUID id) {
        return deposits.findById(id)
                .filter(d -> user.isAdmin() || d.getUserId().equals(user.id()))
                .orElseThrow(() -> ApiException.notFound("Вклад не найден"));
    }

    static LocalDate today() {
        return LocalDate.now(ZONE);
    }
}
