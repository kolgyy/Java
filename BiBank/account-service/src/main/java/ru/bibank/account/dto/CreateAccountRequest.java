package ru.bibank.account.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import ru.bibank.account.entity.AccountType;
import ru.bibank.account.entity.Currency;

public record CreateAccountRequest(
        @NotNull(message = "Укажите валюту")
        Currency currency,
        AccountType type,
        @Size(max = 100, message = "Название не длиннее 100 символов")
        String name
) {}
