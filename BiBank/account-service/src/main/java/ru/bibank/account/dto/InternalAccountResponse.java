package ru.bibank.account.dto;

import ru.bibank.account.entity.Account;

import java.math.BigDecimal;
import java.util.UUID;

public record InternalAccountResponse(
        UUID id,
        String number,
        Long ownerId,
        String ownerName,
        String currency,
        String type,
        String status,
        BigDecimal balance
) {
    public static InternalAccountResponse from(Account a) {
        return new InternalAccountResponse(a.getId(), a.getNumber(), a.getOwnerId(), a.getOwnerName(),
                a.getCurrency().name(), a.getType().name(), a.getStatus().name(), a.getBalance());
    }
}
