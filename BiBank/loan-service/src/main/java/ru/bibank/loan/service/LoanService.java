package ru.bibank.loan.service;

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
import ru.bibank.loan.dto.LoanResponse;
import ru.bibank.loan.dto.PaymentResponse;
import ru.bibank.loan.dto.RepayRequest;
import ru.bibank.loan.entity.Loan;
import ru.bibank.loan.entity.LoanPayment;
import ru.bibank.loan.entity.LoanStatus;
import ru.bibank.loan.entity.ScheduleItem;
import ru.bibank.loan.repository.LoanPaymentRepository;
import ru.bibank.loan.repository.LoanRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Обслуживание выданных кредитов: просмотр задолженности и виртуальные погашения.
 */
@Service
public class LoanService {

    private final LoanRepository loans;
    private final LoanPaymentRepository payments;
    private final BankingClient bankingClient;
    private final OutboxWriter outbox;
    private final TransactionTemplate tx;

    public LoanService(LoanRepository loans, LoanPaymentRepository payments, BankingClient bankingClient,
                       OutboxWriter outbox, TransactionTemplate tx) {
        this.loans = loans;
        this.payments = payments;
        this.bankingClient = bankingClient;
        this.outbox = outbox;
        this.tx = tx;
    }

    @Transactional(readOnly = true)
    public List<LoanResponse> mine(Long userId) {
        return loans.findAllByUserIdOrderByCreatedAtDesc(userId).stream()
                .filter(l -> l.getStatus() != LoanStatus.CANCELLED)
                .map(l -> LoanResponse.from(l, false, null))
                .toList();
    }

    @Transactional(readOnly = true)
    public LoanResponse get(AuthenticatedUser user, UUID id) {
        Loan loan = owned(user, id);
        var history = payments.findAllByLoanIdOrderByCreatedAtDesc(id).stream().map(PaymentResponse::from).toList();
        return LoanResponse.from(loan, true, history);
    }

    public PaymentResponse repay(AuthenticatedUser user, UUID loanId, RepayRequest request) {
        LoanPayment.Mode mode = request.mode() == null ? LoanPayment.Mode.NEXT : request.mode();
        Loan current = owned(user, loanId);
        bankingClient.ownedActiveAccount(user.id(), request.accountId(), current.getCurrency());

        UUID paymentId = tx.execute(s -> {
            Loan loan = owned(user, loanId);
            if (loan.getStatus() != LoanStatus.ACTIVE) {
                throw ApiException.unprocessable("LOAN_NOT_ACTIVE", "Кредит не активен");
            }
            if (payments.existsByLoanIdAndStatus(loanId, LoanPayment.Status.PENDING)) {
                throw ApiException.conflict("PAYMENT_IN_PROGRESS", "Предыдущий платёж ещё обрабатывается");
            }
            ScheduleItem next = nextPlanned(loan);

            LoanPayment p = new LoanPayment();
            p.setLoanId(loanId);
            p.setAccountId(request.accountId());
            p.setOperationId(UUID.randomUUID());
            p.setMode(mode);
            if (mode == LoanPayment.Mode.FULL) {
                // Полное досрочное погашение: остаток основного долга + проценты текущего периода
                p.setPrincipalPart(loan.getOutstandingPrincipal());
                p.setAmount(loan.getOutstandingPrincipal().add(next.getInterestPart()));
            } else {
                p.setPrincipalPart(next.getPrincipalPart());
                p.setAmount(next.getPaymentAmount());
            }
            p.setStatus(LoanPayment.Status.PENDING);
            payments.save(p);
            return p.getId();
        });

        LoanPayment result = process(paymentId);
        if (result.getStatus() == LoanPayment.Status.FAILED) {
            throw ApiException.unprocessable("PAYMENT_FAILED", result.getFailureReason());
        }
        return PaymentResponse.from(result);
    }

    /**
     * Выполняет (или довыполняет) платёж. Повтор безопасен благодаря operationId.
     */
    public LoanPayment process(UUID paymentId) {
        LoanPayment payment = payments.findById(paymentId).orElseThrow();
        if (payment.getStatus() != LoanPayment.Status.PENDING) {
            return payment;
        }
        Loan loan = loans.findById(payment.getLoanId()).orElseThrow();
        var outcome = bankingClient.execute(payment.getOperationId(), BankingClient.OperationType.LOAN_REPAYMENT,
                loan.getUserId(), payment.getAccountId(), null, payment.getAmount(),
                "Погашение кредита «" + loan.getProductName() + "»", loan.getId().toString());

        if (outcome.completed()) {
            return tx.execute(s -> applyCompleted(paymentId));
        }
        if (outcome.failed()) {
            return markFailed(paymentId, outcome.failureReason());
        }
        return payment;
    }

    private LoanPayment applyCompleted(UUID paymentId) {
        LoanPayment p = payments.findById(paymentId).orElseThrow();
        if (p.getStatus() != LoanPayment.Status.PENDING) {
            return p;
        }
        Loan loan = loans.findById(p.getLoanId()).orElseThrow();
        List<ScheduleItem> planned = loan.getSchedule().stream()
                .filter(i -> i.getStatus() == ScheduleItem.Status.PLANNED).toList();
        Instant now = Instant.now();
        if (p.getMode() == LoanPayment.Mode.FULL) {
            planned.forEach(i -> {
                i.setStatus(ScheduleItem.Status.PAID);
                i.setPaidAt(now);
            });
            loan.setOutstandingPrincipal(BigDecimal.ZERO);
        } else if (!planned.isEmpty()) {
            planned.get(0).setStatus(ScheduleItem.Status.PAID);
            planned.get(0).setPaidAt(now);
            loan.setOutstandingPrincipal(loan.getOutstandingPrincipal().subtract(p.getPrincipalPart()).max(BigDecimal.ZERO));
        }
        p.setStatus(LoanPayment.Status.COMPLETED);
        p.setCompletedAt(now);

        outbox.write(Topics.LOAN_EVENTS, EventEnvelope.of(EventTypes.LOAN_PAYMENT_MADE, "loan-service",
                loan.getUserId(), loan.getId(), Map.of("amount", p.getAmount(), "currency", loan.getCurrency(),
                        "remaining", loan.getOutstandingPrincipal(), "productName", loan.getProductName())));

        boolean allPaid = loan.getSchedule().stream().allMatch(i -> i.getStatus() == ScheduleItem.Status.PAID);
        if (allPaid) {
            loan.setStatus(LoanStatus.CLOSED);
            loan.setClosedAt(now);
            outbox.write(Topics.LOAN_EVENTS, EventEnvelope.of(EventTypes.LOAN_CLOSED, "loan-service",
                    loan.getUserId(), loan.getId(), Map.of("productName", loan.getProductName())));
        }
        return p;
    }

    private LoanPayment markFailed(UUID paymentId, String reason) {
        return tx.execute(s -> {
            LoanPayment p = payments.findById(paymentId).orElseThrow();
            if (p.getStatus() == LoanPayment.Status.PENDING) {
                p.setStatus(LoanPayment.Status.FAILED);
                p.setFailureReason(reason);
                p.setCompletedAt(Instant.now());
            }
            return p;
        });
    }

    private static ScheduleItem nextPlanned(Loan loan) {
        return loan.getSchedule().stream()
                .filter(i -> i.getStatus() == ScheduleItem.Status.PLANNED)
                .findFirst()
                .orElseThrow(() -> ApiException.unprocessable("LOAN_PAID", "Кредит уже погашен"));
    }

    private Loan owned(AuthenticatedUser user, UUID id) {
        return loans.findById(id)
                .filter(l -> l.getStatus() != LoanStatus.CANCELLED)
                .filter(l -> user.isAdmin() || l.getUserId().equals(user.id()))
                .orElseThrow(() -> ApiException.notFound("Кредит не найден"));
    }
}
