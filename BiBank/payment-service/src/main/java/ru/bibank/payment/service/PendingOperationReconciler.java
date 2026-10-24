package ru.bibank.payment.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ru.bibank.payment.config.PaymentProperties;
import ru.bibank.payment.entity.Operation;
import ru.bibank.payment.repository.OperationRepository;

import java.time.Instant;

/**
 * Довыполняет операции, оставшиеся в статусе PENDING из-за недоступности Account Service.
 */
@Component
public class PendingOperationReconciler {

    private static final Logger log = LoggerFactory.getLogger(PendingOperationReconciler.class);

    private final OperationRepository repository;
    private final OperationExecutor executor;
    private final PaymentProperties properties;

    public PendingOperationReconciler(OperationRepository repository, OperationExecutor executor,
                                      PaymentProperties properties) {
        this.repository = repository;
        this.executor = executor;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${bibank.payment.reconcile-interval-ms:10000}")
    public void reconcile() {
        Instant before = Instant.now().minus(properties.retryDelay());
        for (Operation op : repository.findStalePending(before, properties.maxAttempts())) {
            try {
                Operation result = executor.execute(op.getId());
                log.info("Reconciled operation {} → {}", op.getId(), result.getStatus());
            } catch (Exception e) {
                log.warn("Reconcile of {} failed: {}", op.getId(), e.getMessage());
            }
        }
    }
}
