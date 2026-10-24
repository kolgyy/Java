package ru.bibank.payment.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Финансовая операция: перевод, пополнение, движение по кредиту или вкладу.
 * Идентификатор операции одновременно является ключом идемпотентности в Account Service.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "operations")
public class Operation {

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OperationType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OperationStatus status;

    @Column(name = "initiator_id", nullable = false)
    private Long initiatorId;

    @Column(name = "idempotency_key", length = 100)
    private String idempotencyKey;

    @Column(name = "from_account_id")
    private UUID fromAccountId;

    @Column(name = "from_account_number", length = 20)
    private String fromAccountNumber;

    @Column(name = "from_owner_id")
    private Long fromOwnerId;

    @Column(name = "from_owner_name")
    private String fromOwnerName;

    @Column(name = "to_account_id")
    private UUID toAccountId;

    @Column(name = "to_account_number", length = 20)
    private String toAccountNumber;

    @Column(name = "to_owner_id")
    private Long toOwnerId;

    @Column(name = "to_owner_name")
    private String toOwnerName;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "credited_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal creditedAmount;

    @Column(name = "credited_currency", nullable = false, length = 3)
    private String creditedCurrency;

    @Column(name = "exchange_rate", precision = 19, scale = 8)
    private BigDecimal exchangeRate;

    @Column(length = 255)
    private String description;

    @Column(name = "external_ref", length = 100)
    private String externalRef;

    @Column(name = "failure_code", length = 50)
    private String failureCode;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    @Column(nullable = false)
    private int attempts;

    @Version
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @PrePersist
    void prePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }

    public void complete() {
        requirePending();
        status = OperationStatus.COMPLETED;
        completedAt = Instant.now();
    }

    public void fail(String code, String reason) {
        requirePending();
        status = OperationStatus.FAILED;
        failureCode = code;
        failureReason = reason;
        completedAt = Instant.now();
    }

    private void requirePending() {
        if (status.isFinal()) {
            throw new IllegalStateException("Operation " + id + " is already " + status);
        }
    }
}
