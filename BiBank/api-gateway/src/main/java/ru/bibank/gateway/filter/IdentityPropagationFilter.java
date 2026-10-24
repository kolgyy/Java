package ru.bibank.gateway.filter;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import ru.bibank.gateway.security.JwtAuthenticationManager.GatewayUser;

import java.util.UUID;

/**
 * Удаляет из входящего запроса служебные заголовки X-User-* (их нельзя подделать снаружи),
 * добавляет сведения о пользователе и X-Request-Id для сквозной трассировки.
 * Сервисы принимают решения по JWT, а не по этим заголовкам.
 */
@Component
public class IdentityPropagationFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String requestId = exchange.getRequest().getHeaders().getFirst("X-Request-Id");
        String rid = requestId == null || requestId.isBlank() ? UUID.randomUUID().toString() : requestId;

        return ReactiveSecurityContextHolder.getContext()
                .mapNotNull(ctx -> ctx.getAuthentication() == null ? null : ctx.getAuthentication().getPrincipal())
                .filter(GatewayUser.class::isInstance)
                .cast(GatewayUser.class)
                .map(user -> mutate(exchange, rid, user))
                .defaultIfEmpty(mutate(exchange, rid, null))
                .flatMap(chain::filter);
    }

    private static ServerWebExchange mutate(ServerWebExchange exchange, String requestId, GatewayUser user) {
        ServerHttpRequest request = exchange.getRequest().mutate()
                .headers(h -> {
                    h.remove("X-User-Id");
                    h.remove("X-User-Email");
                    h.remove("X-User-Role");
                    h.remove("X-Internal-Token");
                    h.set("X-Request-Id", requestId);
                    if (user != null) {
                        h.set("X-User-Id", String.valueOf(user.id()));
                        h.set("X-User-Email", user.email());
                        h.set("X-User-Role", user.role());
                    }
                })
                .build();
        exchange.getResponse().getHeaders().set("X-Request-Id", requestId);
        return exchange.mutate().request(request).build();
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
