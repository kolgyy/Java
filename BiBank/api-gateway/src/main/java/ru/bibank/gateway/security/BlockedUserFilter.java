package ru.bibank.gateway.security;

import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import ru.bibank.gateway.web.JsonErrorWriter;

import java.time.Duration;

/**
 * Мгновенная блокировка: Auth Service при блокировке пользователя ставит ключ blocked:{userId} в Redis,
 * и шлюз отклоняет его запросы, не дожидаясь истечения access token.
 * При недоступности Redis проверка пропускается (fail-open), чтобы не останавливать весь банк.
 */

public class BlockedUserFilter implements WebFilter {

    private final ReactiveStringRedisTemplate redis;
    private final JsonErrorWriter errorWriter;

    public BlockedUserFilter(ReactiveStringRedisTemplate redis, JsonErrorWriter errorWriter) {
        this.redis = redis;
        this.errorWriter = errorWriter;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        return ReactiveSecurityContextHolder.getContext()
                .mapNotNull(ctx -> ctx.getAuthentication() == null ? null : ctx.getAuthentication().getPrincipal())
                .filter(JwtAuthenticationManager.GatewayUser.class::isInstance)
                .cast(JwtAuthenticationManager.GatewayUser.class)
                .flatMap(user -> redis.hasKey("blocked:" + user.id())
                        .timeout(Duration.ofMillis(500))
                        .onErrorReturn(false))
                .defaultIfEmpty(false)
                .flatMap(blocked -> blocked
                        ? errorWriter.write(exchange, HttpStatus.FORBIDDEN, "USER_BLOCKED", "Учётная запись заблокирована")
                        : chain.filter(exchange));
    }
}
