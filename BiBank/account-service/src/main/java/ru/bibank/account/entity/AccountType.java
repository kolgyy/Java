package ru.bibank.account.entity;

public enum AccountType {
    MAIN,        // основной (текущий)
    SAVINGS,     // накопительный
    SYSTEM       // технический счёт банка (контрагент для пополнений, кредитов и вкладов)
}
