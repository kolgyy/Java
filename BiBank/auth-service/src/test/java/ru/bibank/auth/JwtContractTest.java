package ru.bibank.auth;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import ru.bibank.auth.config.JwtProperties;
import ru.bibank.auth.entity.Role;
import ru.bibank.auth.entity.User;
import ru.bibank.auth.security.JwtService;
import ru.bibank.common.security.AuthenticatedUser;
import ru.bibank.common.security.JwtTokenParser;
import ru.bibank.common.security.SecurityProperties;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Контракт между Auth Service (выпускает токен) и остальными сервисами (проверяют его через common).
 */
class JwtContractTest {

    private static final String SECRET = "contract-test-secret-contract-test-secret-42";

    @Test
    void accessTokenCarriesIdentityForOtherServices() {
        String token = service(SECRET, Duration.ofMinutes(15)).generateAccessToken(user());

        AuthenticatedUser parsed = new JwtTokenParser(SECRET).parse(token).orElseThrow();

        assertThat(parsed.id()).isEqualTo(7L);
        assertThat(parsed.email()).isEqualTo("anna@bibank.local");
        assertThat(parsed.role()).isEqualTo("ROLE_ADMIN");
        assertThat(parsed.name()).isEqualTo("Анна Смирнова");
        assertThat(parsed.isAdmin()).isTrue();
    }

    @Test
    void tokenSignedWithAnotherKeyIsRejected() {
        String token = service("another-secret-another-secret-another-secret", Duration.ofMinutes(15)).generateAccessToken(user());
        assertThat(new JwtTokenParser(SECRET).parse(token)).isEmpty();
    }

    @Test
    void tamperedAndExpiredTokensAreRejected() {
        JwtTokenParser parser = new JwtTokenParser(SECRET);
        String token = service(SECRET, Duration.ofMinutes(15)).generateAccessToken(user());
        String[] parts = token.split("\\.");
        String forged = parts[0] + "." + parts[1].substring(0, parts[1].length() - 2) + "AA." + parts[2];

        assertThat(parser.parse(forged)).isEmpty();
        assertThat(parser.parse(service(SECRET, Duration.ofSeconds(-5)).generateAccessToken(user()))).isEmpty();
        assertThat(parser.parse("not-a-jwt")).isEmpty();
    }

    @Test
    void refreshTokenIsOpaqueAndRandom() {
        JwtService service = service(SECRET, Duration.ofMinutes(15));
        String a = service.generateRefreshToken();
        String b = service.generateRefreshToken();

        assertThat(a).hasSize(43).isNotEqualTo(b).doesNotContain(".");
        assertThat(new JwtTokenParser(SECRET).parse(a)).isEmpty();
    }

    private static JwtService service(String secret, Duration ttl) {
        return new JwtService(new JwtProperties(ttl, Duration.ofDays(7)), new SecurityProperties(secret, "internal"));
    }

    private static User user() {
        User user = new User();
        ReflectionTestUtils.setField(user, "id", 7L);
        user.setEmail("anna@bibank.local");
        user.setFullName("Анна Смирнова");
        user.setRole(Role.ROLE_ADMIN);
        return user;
    }
}
