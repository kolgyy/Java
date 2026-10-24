package ru.bibank.gateway.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import reactor.core.publisher.Mono;
import ru.bibank.gateway.security.JwtAuthenticationManager.GatewayUser;

import java.net.InetSocketAddress;
import java.util.Optional;

/**
 * Маршрутизация запросов к микросервисам.
 * Ограничение частоты (Redis, token bucket): вход и регистрация — по IP, финансовые операции — по пользователю.
 */
@Configuration
public class RoutesConfig {

    @Bean
    public RouteLocator routes(RouteLocatorBuilder builder, GatewayProperties properties,
                               KeyResolver ipKeyResolver, KeyResolver userKeyResolver) {
        var s = properties.services();
        return builder.routes()
                .route("auth-public", r -> r.path("/api/auth/**")
                        .filters(f -> f.requestRateLimiter(c -> c
                                .setRateLimiter(authRateLimiter())
                                .setKeyResolver(ipKeyResolver)))
                        .uri(s.auth()))
                .route("auth-users", r -> r.path("/api/users/**", "/api/admin/users/**").uri(s.auth()))
                .route("accounts", r -> r.path("/api/accounts/**", "/api/admin/accounts/**").uri(s.account()))
                .route("payments-write", r -> r.path("/api/transfers", "/api/top-ups").and().method("POST")
                        .filters(f -> f.requestRateLimiter(c -> c
                                .setRateLimiter(paymentRateLimiter())
                                .setKeyResolver(userKeyResolver)))
                        .uri(s.payment()))
                .route("payments", r -> r.path("/api/transfers/**", "/api/top-ups/**", "/api/transactions/**",
                        "/api/exchange-rates/**", "/api/admin/transactions/**").uri(s.payment()))
                .route("loans", r -> r.path("/api/loans/**", "/api/deposits/**",
                        "/api/admin/loans/**", "/api/admin/deposits/**").uri(s.loan()))
                .route("notifications", r -> r.path("/api/notifications/**").uri(s.notification()))
                // OpenAPI-спецификации сервисов для общего Swagger UI
                .route("docs-auth", r -> r.path("/api-docs/auth").filters(f -> f.setPath("/v3/api-docs")).uri(s.auth()))
                .route("docs-account", r -> r.path("/api-docs/account").filters(f -> f.setPath("/v3/api-docs")).uri(s.account()))
                .route("docs-payment", r -> r.path("/api-docs/payment").filters(f -> f.setPath("/v3/api-docs")).uri(s.payment()))
                .route("docs-loan", r -> r.path("/api-docs/loan").filters(f -> f.setPath("/v3/api-docs")).uri(s.loan()))
                .route("docs-notification", r -> r.path("/api-docs/notification").filters(f -> f.setPath("/v3/api-docs")).uri(s.notification()))
                .build();
    }

    /** 5 запросов в секунду, всплеск до 20 — защита входа от перебора паролей. */
    @Bean
    @Primary
    public RedisRateLimiter authRateLimiter() {
        return new RedisRateLimiter(5, 20, 1);
    }

    /** 10 финансовых операций в секунду на пользователя, всплеск до 20. */
    @Bean
    public RedisRateLimiter paymentRateLimiter() {
        return new RedisRateLimiter(10, 20, 1);
    }

    @Bean
    @Primary
    public KeyResolver ipKeyResolver() {
        return exchange -> {
            String forwarded = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                return Mono.just(forwarded.split(",")[0].trim());
            }
            return Mono.just(Optional.ofNullable(exchange.getRequest().getRemoteAddress())
                    .map(InetSocketAddress::getAddress)
                    .map(a -> a.getHostAddress())
                    .orElse("unknown"));
        };
    }

    @Bean
    public KeyResolver userKeyResolver() {
        return exchange -> ReactiveSecurityContextHolder.getContext()
                .mapNotNull(ctx -> ctx.getAuthentication() == null ? null : ctx.getAuthentication().getPrincipal())
                .filter(GatewayUser.class::isInstance)
                .map(p -> "user:" + ((GatewayUser) p).id())
                .defaultIfEmpty("anonymous");
    }
}
