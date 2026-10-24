package ru.bibank.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * Проверка подписи и срока действия access token. Используется каждым сервисом
 * самостоятельно, чтобы не полагаться только на проверку в API Gateway.
 */
public class JwtTokenParser {

    private final SecretKey key;

    public JwtTokenParser(String secret) {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("bibank.security.jwt-secret must be at least 32 bytes");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public Optional<AuthenticatedUser> parse(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            if (!"access".equals(claims.get("type", String.class))) {
                return Optional.empty();
            }
            Number userId = claims.get("userId", Number.class);
            String role = claims.get("role", String.class);
            if (userId == null || role == null) {
                return Optional.empty();
            }
            return Optional.of(new AuthenticatedUser(userId.longValue(), claims.getSubject(), role,
                    claims.get("name", String.class)));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
