package ru.bibank.loan.entity;

/**
 * PENDING_DISBURSEMENT → ACTIVE → CLOSED; PENDING_DISBURSEMENT → CANCELLED (компенсация, если зачисление не удалось).
 */
public enum LoanStatus {
    PENDING_DISBURSEMENT,
    ACTIVE,
    CLOSED,
    CANCELLED
}
