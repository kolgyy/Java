package ru.bibank.gateway.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import ru.bibank.gateway.config.GatewayProperties;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Проверяет подпись, срок действия и тип access token.
 */
@Component
public class JwtAuthenticationManager implements ReactiveAuthenticationManager {

    public record GatewayUser(Long id, String email, String role) {
    }

    private final SecretKey key;

    public JwtAuthenticationManager(GatewayProperties properties) {
        this.key = Keys.hmacShaKeyFor(properties.jwtSecret().getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public Mono<Authentication> authenticate(Authentication authentication) {
        String token = (String) authentication.getCredentials();
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            Number userId = claims.get("userId", Number.class);
            String role = claims.get("role", String.class);
            if (!"access".equals(claims.get("type", String.class)) || userId == null || role == null) {
                return Mono.error(new BadCredentialsException("Invalid token"));
            }
            var user = new GatewayUser(userId.longValue(), claims.getSubject(), role);
            return Mono.just(new UsernamePasswordAuthenticationToken(user, token,
                    List.of(new SimpleGrantedAuthority(role))));
        } catch (JwtException | IllegalArgumentException e) {
            return Mono.error(new BadCredentialsException("Invalid token", e));
        }
    }
}
