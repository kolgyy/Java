package ru.bibank.loan.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ru.bibank.loan.entity.Deposit;
import ru.bibank.loan.entity.DepositStatus;
import ru.bibank.loan.entity.LoanPayment;
import ru.bibank.loan.entity.LoanStatus;
import ru.bibank.loan.repository.DepositRepository;
import ru.bibank.loan.repository.LoanPaymentRepository;
import ru.bibank.loan.repository.LoanRepository;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Планировщики Loan Service:
 * <ul>
 *     <li>довыполнение незавершённых шагов (выдача кредита, платёж, открытие/закрытие вклада);</li>
 *     <li>ежедневное начисление процентов по вкладам и закрытие вкладов в дату окончания.</li>
 * </ul>
 */
@Component
public class BackgroundJobs {

    private static final Logger log = LoggerFactory.getLogger(BackgroundJobs.class);

    private final LoanRepository loans;
    private final LoanPaymentRepository payments;
    private final DepositRepository deposits;
    private final LoanApplicationService applicationService;
    private final LoanService loanService;
    private final DepositService depositService;

    public BackgroundJobs(LoanRepository loans, LoanPaymentRepository payments, DepositRepository deposits,
                          LoanApplicationService applicationService, LoanService loanService,
                          DepositService depositService) {
        this.loans = loans;
        this.payments = payments;
        this.deposits = deposits;
        this.applicationService = applicationService;
        this.loanService = loanService;
        this.depositService = depositService;
    }

    @Scheduled(fixedDelayString = "${bibank.loan.retry-interval-ms:15000}", initialDelay = 15000)
    public void retryPending() {
        Instant before = Instant.now().minus(10, ChronoUnit.SECONDS);
        safe(() -> loans.findAllByStatus(LoanStatus.PENDING_DISBURSEMENT)
                .forEach(l -> safe(() -> applicationService.disburse(l.getId()))));
        safe(() -> payments.findAllByStatusAndCreatedAtBefore(LoanPayment.Status.PENDING, before)
                .forEach(p -> safe(() -> loanService.process(p.getId()))));
        safe(() -> deposits.findAllByStatusIn(List.of(DepositStatus.OPENING, DepositStatus.CLOSING)).forEach(d -> safe(() -> {
            if (d.getStatus() == DepositStatus.OPENING) {
                depositService.processOpening(d.getId());
            } else {
                depositService.processClosing(d.getId());
            }
        })));
    }

    /**
     * Ежедневное начисление; запуск каждый час делает задачу устойчивой к простоям сервиса.
     */
    @Scheduled(cron = "${bibank.loan.accrual-cron:0 5 * * * *}", zone = "Europe/Moscow")
    public void accrueInterest() {
        var today = DepositService.today();
        int matured = 0;
        for (Deposit d : deposits.findAllByStatus(DepositStatus.ACTIVE)) {
            try {
                if (depositService.accrue(d.getId(), today)) {
                    matured++;
                }
            } catch (Exception e) {
                log.warn("Accrual failed for deposit {}: {}", d.getId(), e.getMessage());
            }
        }
        if (matured > 0) {
            log.info("Matured deposits closed: {}", matured);
        }
    }

    /**
     * Демонстрационный режим: «проматывает» N дней для всех активных вкладов.
     */
    public int simulateDays(int days) {
        int processed = 0;
        for (Deposit d : deposits.findAllByStatus(DepositStatus.ACTIVE)) {
            depositService.accrue(d.getId(), d.getAccruedThrough().plusDays(days));
            processed++;
        }
        return processed;
    }

    private static void safe(Runnable r) {
        try {
            r.run();
        } catch (Exception e) {
            log.warn("Background step failed: {}", e.getMessage());
        }
    }
}
