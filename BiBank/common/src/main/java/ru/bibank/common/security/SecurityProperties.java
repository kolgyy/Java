package ru.bibank.common.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Общие настройки безопасности сервисов.
 *
 * @param jwtSecret     ключ подписи JWT (HS256, не короче 32 байт)
 * @param internalToken секрет для межсервисных вызовов /internal/**
 */
@ConfigurationProperties(prefix = "bibank.security")
public record SecurityProperties(String jwtSecret, String internalToken) {
}
