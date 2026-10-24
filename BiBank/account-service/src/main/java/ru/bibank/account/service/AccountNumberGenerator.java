package ru.bibank.account.service;

import org.springframework.stereotype.Component;
import ru.bibank.account.entity.Currency;

import java.security.SecureRandom;

/**
 * Номер счёта из 20 цифр по мотивам российского формата:
 * 40817 (счёт физлица) + код валюты + 12 случайных цифр.
 */
@Component
public class AccountNumberGenerator {

    private final SecureRandom random = new SecureRandom();

    public String generate(Currency currency) {
        StringBuilder sb = new StringBuilder("40817").append(currency.accountCode());
        for (int i = 0; i < 12; i++) {
            sb.append(random.nextInt(10));
        }
        return sb.toString();
    }
}
