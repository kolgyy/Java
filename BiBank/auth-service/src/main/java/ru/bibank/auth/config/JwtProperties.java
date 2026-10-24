package ru.bibank.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * @param accessTokenTtl  срок жизни access token
 * @param refreshTokenTtl срок жизни refresh token
 */
@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(
        Duration accessTokenTtl,
        Duration refreshTokenTtl) {
}
