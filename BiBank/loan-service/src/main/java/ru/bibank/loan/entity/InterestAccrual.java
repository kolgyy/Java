package ru.bibank.loan.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Запись о начислении процентов за один день. Пара (вклад, дата) уникальна,
 * поэтому повторный запуск начисления не удваивает проценты.
 */
@Getter
@NoArgsConstructor
@Entity
@Table(name = "interest_accruals")
public class InterestAccrual {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "deposit_id", nullable = false)
    private UUID depositId;

    @Column(name = "accrual_date", nullable = false)
    private LocalDate accrualDate;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal base;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public InterestAccrual(UUID depositId, LocalDate accrualDate, BigDecimal base, BigDecimal amount) {
        this.depositId = depositId;
        this.accrualDate = accrualDate;
        this.base = base;
        this.amount = amount;
        this.createdAt = Instant.now();
    }
}
