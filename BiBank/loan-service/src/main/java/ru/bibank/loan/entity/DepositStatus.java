package ru.bibank.loan.entity;

/**
 * OPENING → ACTIVE → CLOSING → CLOSED; OPENING → FAILED, если списание не прошло.
 */
public enum DepositStatus {
    OPENING,
    ACTIVE,
    CLOSING,
    CLOSED,
    FAILED
}
