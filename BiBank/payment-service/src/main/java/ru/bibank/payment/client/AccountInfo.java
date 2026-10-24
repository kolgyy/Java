package ru.bibank.payment.client;

import java.math.BigDecimal;
import java.util.UUID;

public record AccountInfo(
        UUID id,
        String number,
        Long ownerId,
        String ownerName,
        String currency,
        String type,
        String status,
        BigDecimal balance
) {
    public boolean isActive() {
        return "ACTIVE".equals(status);
    }

    public boolean isSystem() {
        return "SYSTEM".equals(type);
    }
}
