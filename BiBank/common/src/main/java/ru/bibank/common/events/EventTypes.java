package ru.bibank.common.events;

public final class EventTypes {

    public static final String USER_REGISTERED = "UserRegistered";
    public static final String USER_STATUS_CHANGED = "UserStatusChanged";

    public static final String ACCOUNT_CREATED = "AccountCreated";
    public static final String ACCOUNT_STATUS_CHANGED = "AccountStatusChanged";

    public static final String TRANSFER_COMPLETED = "TransferCompleted";
    public static final String TRANSFER_FAILED = "TransferFailed";
    public static final String TRANSFER_RECEIVED = "TransferReceived";
    public static final String TOP_UP_COMPLETED = "TopUpCompleted";

    public static final String LOAN_APPLICATION_SUBMITTED = "LoanApplicationSubmitted";
    public static final String LOAN_APPLICATION_STATUS_CHANGED = "LoanApplicationStatusChanged";
    public static final String LOAN_PAYMENT_MADE = "LoanPaymentMade";
    public static final String LOAN_CLOSED = "LoanClosed";

    public static final String DEPOSIT_OPENED = "DepositOpened";
    public static final String DEPOSIT_CLOSED = "DepositClosed";

    private EventTypes() {
    }
}
