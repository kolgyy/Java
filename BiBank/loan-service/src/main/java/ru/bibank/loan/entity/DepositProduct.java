package ru.bibank.loan.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Накопительный продукт (вклад).
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "deposit_products")
public class DepositProduct {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 50)
    private String code;

    @Column(nullable = false)
    private String name;

    @Column(length = 1000)
    private String description;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "annual_rate", nullable = false, precision = 6, scale = 3)
    private BigDecimal annualRate;

    /** Ставка при досрочном закрытии, % годовых. */
    @Column(name = "early_close_rate", nullable = false, precision = 6, scale = 3)
    private BigDecimal earlyCloseRate;

    @Column(name = "min_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal minAmount;

    @Column(name = "max_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal maxAmount;

    @Column(name = "min_term_months", nullable = false)
    private int minTermMonths;

    @Column(name = "max_term_months", nullable = false)
    private int maxTermMonths;

    /** Капитализация: проценты начисляются на сумму вклада вместе с ранее начисленными процентами. */
    @Column(nullable = false)
    private boolean capitalization;

    @Column(nullable = false)
    private boolean active;
}
