package ru.bibank.loan.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "deposits")
public class Deposit {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "product_name", nullable = false)
    private String productName;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal principal;

    @Column(name = "annual_rate", nullable = false, precision = 6, scale = 3)
    private BigDecimal annualRate;

    @Column(name = "early_close_rate", nullable = false, precision = 6, scale = 3)
    private BigDecimal earlyCloseRate;

    @Column(nullable = false)
    private boolean capitalization;

    @Column(name = "term_months", nullable = false)
    private int termMonths;

    @Column(name = "open_date", nullable = false)
    private LocalDate openDate;

    @Column(name = "maturity_date", nullable = false)
    private LocalDate maturityDate;

    /** Дата, по которую включительно начислены проценты. */
    @Column(name = "accrued_through", nullable = false)
    private LocalDate accruedThrough;

    @Column(name = "accrued_interest", nullable = false, precision = 19, scale = 2)
    private BigDecimal accruedInterest = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DepositStatus status;

    @Column(name = "open_operation_id", nullable = false)
    private UUID openOperationId;

    @Column(name = "close_operation_id")
    private UUID closeOperationId;

    @Column(name = "payout_amount", precision = 19, scale = 2)
    private BigDecimal payoutAmount;

    @Column(name = "payout_interest", precision = 19, scale = 2)
    private BigDecimal payoutInterest;

    @Column(name = "early_closed", nullable = false)
    private boolean earlyClosed;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    @Version
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    @PrePersist
    void prePersist() {
        createdAt = Instant.now();
    }
}
