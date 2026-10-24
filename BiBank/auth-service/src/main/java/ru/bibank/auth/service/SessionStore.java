package ru.bibank.auth.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import ru.bibank.auth.config.JwtProperties;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Optional;
import java.util.Set;

/**
 * Хранилище пользовательских сессий (refresh token) в Redis.
 * <ul>
 *     <li>refresh:{sha256(token)} → userId (TTL = срок жизни refresh token)</li>
 *     <li>sessions:{userId} → множество хешей активных токенов пользователя</li>
 *     <li>blocked:{userId} → признак блокировки, проверяется API Gateway</li>
 * </ul>
 */
@Component
public class SessionStore {

    private static final String REFRESH = "refresh:";
    private static final String SESSIONS = "sessions:";
    private static final String BLOCKED = "blocked:";
    private static final String LOGIN_FAILS = "login-fails:";

    private final StringRedisTemplate redis;
    private final JwtProperties jwtProperties;

    public SessionStore(StringRedisTemplate redis, JwtProperties jwtProperties) {
        this.redis = redis;
        this.jwtProperties = jwtProperties;
    }

    public void save(String refreshToken, Long userId) {
        String hash = hash(refreshToken);
        Duration ttl = jwtProperties.refreshTokenTtl();
        redis.opsForValue().set(REFRESH + hash, userId.toString(), ttl);
        redis.opsForSet().add(SESSIONS + userId, hash);
        redis.expire(SESSIONS + userId, ttl);
    }

    /**
     * Атомарно забирает токен (GETDEL), поэтому один refresh token нельзя использовать дважды.
     */
    public Optional<Long> consume(String refreshToken) {
        String hash = hash(refreshToken);
        String userId = redis.opsForValue().getAndDelete(REFRESH + hash);
        if (userId == null) {
            return Optional.empty();
        }
        redis.opsForSet().remove(SESSIONS + userId, hash);
        return Optional.of(Long.parseLong(userId));
    }

    public void revoke(String refreshToken) {
        consume(refreshToken);
    }

    public void revokeAll(Long userId) {
        Set<String> hashes = redis.opsForSet().members(SESSIONS + userId);
        if (hashes != null) {
            hashes.forEach(h -> redis.delete(REFRESH + h));
        }
        redis.delete(SESSIONS + userId);
    }

    public void setBlocked(Long userId, boolean blocked) {
        if (blocked) {
            redis.opsForValue().set(BLOCKED + userId, "1");
        } else {
            redis.delete(BLOCKED + userId);
        }
    }

    public long registerFailedLogin(String email, Duration window) {
        String key = LOGIN_FAILS + email.toLowerCase();
        Long count = redis.opsForValue().increment(key);
        if (count != null && count == 1) {
            redis.expire(key, window);
        }
        return count == null ? 0 : count;
    }

    public long failedLogins(String email) {
        String value = redis.opsForValue().get(LOGIN_FAILS + email.toLowerCase());
        return value == null ? 0 : Long.parseLong(value);
    }

    public void resetFailedLogins(String email) {
        redis.delete(LOGIN_FAILS + email.toLowerCase());
    }

    static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
