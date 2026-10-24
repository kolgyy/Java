package ru.bibank.payment.entity;

/**
 * PENDING → COMPLETED | FAILED. Переходы из конечных статусов запрещены.
 */
public enum OperationStatus {
    PENDING,
    COMPLETED,
    FAILED;

    public boolean isFinal() {
        return this != PENDING;
    }
}
