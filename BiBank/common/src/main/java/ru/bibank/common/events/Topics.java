package ru.bibank.common.events;

/**
 * Имена топиков Kafka. Ключ сообщения — userId, поэтому события одного пользователя
 * попадают в одну партицию и обрабатываются по порядку.
 */
public final class Topics {

    public static final String USER_EVENTS = "bibank.user-events";
    public static final String ACCOUNT_EVENTS = "bibank.account-events";
    public static final String PAYMENT_EVENTS = "bibank.payment-events";
    public static final String LOAN_EVENTS = "bibank.loan-events";

    public static final String DLT_SUFFIX = ".dlt";

    private Topics() {
    }
}
