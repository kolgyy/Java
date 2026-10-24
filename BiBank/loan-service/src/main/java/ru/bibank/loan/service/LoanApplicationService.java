package ru.bibank.loan.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import ru.bibank.common.events.EventEnvelope;
import ru.bibank.common.events.EventTypes;
import ru.bibank.common.events.Topics;
import ru.bibank.common.outbox.OutboxWriter;
import ru.bibank.common.security.AuthenticatedUser;
import ru.bibank.common.web.ApiException;
import ru.bibank.common.web.PageResponse;
import ru.bibank.loan.client.BankingClient;
import ru.bibank.loan.dto.*;
import ru.bibank.loan.entity.*;
import ru.bibank.loan.repository.LoanApplicationRepository;
import ru.bibank.loan.repository.LoanRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Жизненный цикл кредитной заявки и выдача кредита.
 * <p>
 * Выдача — распределённая операция (Saga): заявка одобряется и кредит создаётся в статусе
 * PENDING_DISBURSEMENT, затем Payment Service зачисляет средства. Если зачисление отклонено,
 * выполняется компенсация: кредит отменяется, заявка переводится в REJECTED.
 */
@Service
public class LoanApplicationService {

    private static final Logger log = LoggerFactory.getLogger(LoanApplicationService.class);

    static final int MAX_OPEN_APPLICATIONS = 3;
    static final BigDecimal MAX_PAYMENT_TO_INCOME = new BigDecimal("0.5");
    static final Long SYSTEM_DECIDER = 0L;

    private final LoanApplicationRepository applications;
    private final LoanRepository loans;
    private final ProductService productService;
    private final BankingClient bankingClient;
    private final OutboxWriter outbox;
    private final TransactionTemplate tx;

    public LoanApplicationService(LoanApplicationRepository applications, LoanRepository loans,
                                  ProductService productService, BankingClient bankingClient,
                                  OutboxWriter outbox, TransactionTemplate tx) {
        this.applications = applications;
        this.loans = loans;
        this.productService = productService;
        this.bankingClient = bankingClient;
        this.outbox = outbox;
        this.tx = tx;
    }

    public LoanCalculationResponse calculate(LoanCalculationRequest request) {
        LoanProduct product = productService.activeLoanProduct(request.productId());
        validateLimits(product, request.amount(), request.termMonths());
        var schedule = AnnuityCalculator.schedule(request.amount(), product.getAnnualRate(), request.termMonths(),
                LocalDate.now().plusMonths(1));
        return new LoanCalculationResponse(request.amount(), product.getCurrency(), product.getAnnualRate(),
                request.termMonths(), schedule.monthlyPayment(), schedule.totalPayment(), schedule.totalInterest(),
                schedule.installments().stream().map(i -> new ScheduleItemResponse(i.number(), i.dueDate(),
                        i.payment(), i.principal(), i.interest(), i.remaining(), "PLANNED")).toList());
    }

    public ApplicationResponse submit(AuthenticatedUser user, CreateApplicationRequest request) {
        LoanProduct product = productService.activeLoanProduct(request.productId());
        validateLimits(product, request.amount(), request.termMonths());
        if (applications.countByUserIdAndStatus(user.id(), ApplicationStatus.SUBMITTED) >= MAX_OPEN_APPLICATIONS) {
            throw ApiException.unprocessable("TOO_MANY_APPLICATIONS",
                    "Нельзя иметь более " + MAX_OPEN_APPLICATIONS + " заявок на рассмотрении");
        }
        var account = bankingClient.ownedActiveAccount(user.id(), request.accountId(), product.getCurrency());

        UUID id = tx.execute(s -> {
            LoanApplication app = new LoanApplication();
            app.setUserId(user.id());
            app.setProduct(product);
            app.setAccountId(account.id());
            app.setAccountNumber(account.number());
            app.setAmount(request.amount());
            app.setCurrency(product.getCurrency());
            app.setTermMonths(request.termMonths());
            app.setMonthlyIncome(request.monthlyIncome());
            app.setPurpose(request.purpose());
            app.setStatus(ApplicationStatus.SUBMITTED);
            applications.save(app);
            outbox.write(Topics.LOAN_EVENTS, EventEnvelope.of(EventTypes.LOAN_APPLICATION_SUBMITTED, "loan-service",
                    user.id(), app.getId(), Map.of("amount", app.getAmount(), "currency", app.getCurrency(),
                            "productName", product.getName(), "termMonths", app.getTermMonths())));
            return app.getId();
        });

        autoDecide(id, product, request);
        return get(user, id);
    }

    /**
     * Упрощённый скоринг: отказ, если платёж больше 50% дохода; автоодобрение до лимита продукта.
     * Остальные заявки ждут решения администратора.
     */
    private void autoDecide(UUID applicationId, LoanProduct product, CreateApplicationRequest request) {
        BigDecimal payment = AnnuityCalculator.monthlyPayment(request.amount(), product.getAnnualRate(),
                request.termMonths());
        if (request.monthlyIncome() != null && request.monthlyIncome().signum() > 0
                && payment.compareTo(request.monthlyIncome().multiply(MAX_PAYMENT_TO_INCOME)) > 0) {
            reject(applicationId, SYSTEM_DECIDER, "Ежемесячный платёж превышает 50% дохода");
        } else if (request.amount().compareTo(product.getAutoApproveLimit()) <= 0) {
            approve(applicationId, SYSTEM_DECIDER, "Автоматическое одобрение");
        }
    }

    public ApplicationResponse approve(UUID applicationId, Long deciderId, String reason) {
        UUID loanId = tx.execute(s -> {
            LoanApplication app = applications.findById(applicationId)
                    .orElseThrow(() -> ApiException.notFound("Заявка не найдена"));
            app.transitionTo(ApplicationStatus.APPROVED, deciderId, reason);

            Loan loan = new Loan();
            loan.setUserId(app.getUserId());
            loan.setApplicationId(app.getId());
            loan.setProductName(app.getProduct().getName());
            loan.setAccountId(app.getAccountId());
            loan.setCurrency(app.getCurrency());
            loan.setPrincipal(app.getAmount());
            loan.setAnnualRate(app.getProduct().getAnnualRate());
            loan.setTermMonths(app.getTermMonths());
            loan.setOutstandingPrincipal(app.getAmount());
            loan.setStatus(LoanStatus.PENDING_DISBURSEMENT);
            loan.setDisbursementOperationId(UUID.randomUUID());
            loan.setFirstPaymentDate(LocalDate.now().plusMonths(1));

            var schedule = AnnuityCalculator.schedule(app.getAmount(), loan.getAnnualRate(), app.getTermMonths(),
                    loan.getFirstPaymentDate());
            loan.setMonthlyPayment(schedule.monthlyPayment());
            for (var i : schedule.installments()) {
                ScheduleItem item = new ScheduleItem();
                item.setLoan(loan);
                item.setInstallmentNumber(i.number());
                item.setDueDate(i.dueDate());
                item.setPaymentAmount(i.payment());
                item.setPrincipalPart(i.principal());
                item.setInterestPart(i.interest());
                item.setRemainingPrincipal(i.remaining());
                loan.getSchedule().add(item);
            }
            loans.save(loan);
            app.setLoanId(loan.getId());
            return loan.getId();
        });
        disburse(loanId);
        return ApplicationResponse.from(applications.findById(applicationId).orElseThrow());
    }

    /**
     * Шаг саги: зачисление кредита. Повторяется планировщиком, пока результат неизвестен.
     */
    public void disburse(UUID loanId) {
        Loan loan = loans.findById(loanId).orElseThrow();
        if (loan.getStatus() != LoanStatus.PENDING_DISBURSEMENT) {
            return;
        }
        var outcome = bankingClient.execute(loan.getDisbursementOperationId(),
                BankingClient.OperationType.LOAN_DISBURSEMENT, loan.getUserId(), null, loan.getAccountId(),
                loan.getPrincipal(), "Выдача кредита «" + loan.getProductName() + "»", loan.getId().toString());

        if (outcome.completed()) {
            tx.executeWithoutResult(s -> {
                Loan l = loans.findById(loanId).orElseThrow();
                if (l.getStatus() != LoanStatus.PENDING_DISBURSEMENT) {
                    return;
                }
                l.setStatus(LoanStatus.ACTIVE);
                l.setIssuedAt(Instant.now());
                LoanApplication app = applications.findById(l.getApplicationId()).orElseThrow();
                publishStatus(app, null);
            });
        } else if (outcome.failed()) {
            // Компенсация: кредит не выдан — отменяем его и отклоняем заявку
            tx.executeWithoutResult(s -> {
                Loan l = loans.findById(loanId).orElseThrow();
                if (l.getStatus() != LoanStatus.PENDING_DISBURSEMENT) {
                    return;
                }
                l.setStatus(LoanStatus.CANCELLED);
                LoanApplication app = applications.findById(l.getApplicationId()).orElseThrow();
                app.setStatus(ApplicationStatus.REJECTED);
                app.setDecisionReason("Не удалось зачислить средства: " + outcome.failureReason());
                publishStatus(app, app.getDecisionReason());
            });
            log.warn("Loan {} disbursement failed: {}", loanId, outcome.failureReason());
        }
    }

    public ApplicationResponse reject(UUID applicationId, Long deciderId, String reason) {
        return tx.execute(s -> {
            LoanApplication app = applications.findById(applicationId)
                    .orElseThrow(() -> ApiException.notFound("Заявка не найдена"));
            app.transitionTo(ApplicationStatus.REJECTED, deciderId, reason);
            publishStatus(app, reason);
            return ApplicationResponse.from(app);
        });
    }

    @Transactional
    public ApplicationResponse cancel(AuthenticatedUser user, UUID applicationId) {
        LoanApplication app = owned(user, applicationId);
        app.transitionTo(ApplicationStatus.CANCELLED, user.id(), "Отменена клиентом");
        publishStatus(app, null);
        return ApplicationResponse.from(app);
    }

    @Transactional(readOnly = true)
    public List<ApplicationResponse> mine(Long userId) {
        return applications.findAllByUserIdOrderByCreatedAtDesc(userId).stream().map(ApplicationResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public ApplicationResponse get(AuthenticatedUser user, UUID id) {
        return ApplicationResponse.from(owned(user, id));
    }

    @Transactional(readOnly = true)
    public PageResponse<ApplicationResponse> adminList(ApplicationStatus status, int page, int size) {
        var pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        var result = status == null ? applications.findAll(pageable) : applications.findAllByStatus(status, pageable);
        return PageResponse.of(result, ApplicationResponse::from);
    }

    private LoanApplication owned(AuthenticatedUser user, UUID id) {
        return applications.findById(id)
                .filter(a -> user.isAdmin() || a.getUserId().equals(user.id()))
                .orElseThrow(() -> ApiException.notFound("Заявка не найдена"));
    }

    private void publishStatus(LoanApplication app, String reason) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("applicationId", app.getId().toString());
        payload.put("status", app.getStatus().name());
        payload.put("amount", app.getAmount());
        payload.put("currency", app.getCurrency());
        payload.put("productName", app.getProduct().getName());
        if (reason != null) {
            payload.put("reason", reason);
        }
        outbox.write(Topics.LOAN_EVENTS, EventEnvelope.of(EventTypes.LOAN_APPLICATION_STATUS_CHANGED, "loan-service",
                app.getUserId(), app.getId(), payload));
    }

    static void validateLimits(LoanProduct product, BigDecimal amount, int termMonths) {
        if (amount.compareTo(product.getMinAmount()) < 0 || amount.compareTo(product.getMaxAmount()) > 0) {
            throw ApiException.unprocessable("AMOUNT_OUT_OF_RANGE", "Сумма должна быть от "
                    + product.getMinAmount().toPlainString() + " до " + product.getMaxAmount().toPlainString());
        }
        if (termMonths < product.getMinTermMonths() || termMonths > product.getMaxTermMonths()) {
            throw ApiException.unprocessable("TERM_OUT_OF_RANGE", "Срок должен быть от "
                    + product.getMinTermMonths() + " до " + product.getMaxTermMonths() + " месяцев");
        }
    }
}
