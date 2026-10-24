package ru.bibank.account.dto;

import ru.bibank.account.entity.Account;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record AccountResponse(
        UUID id,
        String number,
        String name,
        Long ownerId,
        String currency,
        String type,
        String status,
        BigDecimal balance,
        Instant createdAt,
        Instant updatedAt
) {
    public static AccountResponse from(Account a) {
        return new AccountResponse(a.getId(), a.getNumber(), a.getName(), a.getOwnerId(), a.getCurrency().name(),
                a.getType().name(), a.getStatus().name(), a.getBalance(), a.getCreatedAt(), a.getUpdatedAt());
    }
}
