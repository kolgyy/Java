package ru.bibank.payment.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.util.UUID;

public record TransferRequest(
        @NotNull(message = "Выберите счёт списания")
        UUID fromAccountId,

        @NotBlank(message = "Укажите номер счёта получателя")
        @Pattern(regexp = "^\\d{20}$", message = "Номер счёта должен состоять из 20 цифр")
        String toAccountNumber,

        @NotNull(message = "Укажите сумму")
        @DecimalMin(value = "0.01", message = "Минимальная сумма перевода 0.01")
        @Digits(integer = 15, fraction = 2, message = "Не более двух знаков после запятой")
        BigDecimal amount,

        @Size(max = 255, message = "Комментарий не длиннее 255 символов")
        String description
) {}
