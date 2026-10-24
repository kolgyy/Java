package ru.bibank.account.dto;

/**
 * Публичные сведения о счёте получателя для подтверждения перевода (имя замаскировано).
 */
public record AccountLookupResponse(String number, String currency, String ownerName, boolean active) {
}
