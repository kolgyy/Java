package ru.bibank.loan.dto;

import jakarta.validation.constraints.NotNull;
import ru.bibank.loan.entity.LoanPayment;

import java.util.UUID;

/**
 * @param mode NEXT — очередной платёж по графику, FULL — полное досрочное погашение остатка долга
 */
public record RepayRequest(@NotNull UUID accountId, LoanPayment.Mode mode) {
}
