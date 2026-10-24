package ru.bibank.account.entity;

public enum Currency {
    RUB("810"),
    USD("840"),
    EUR("978");

    /** Код валюты в номере счёта (6–8 разряды). */
    private final String accountCode;

    Currency(String accountCode) {
        this.accountCode = accountCode;
    }

    public String accountCode() {
        return accountCode;
    }
}
