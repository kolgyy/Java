package ru.bibank.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;
import ru.bibank.gateway.security.BlockedUserFilter;
import ru.bibank.gateway.security.JwtAuthenticationManager;
import ru.bibank.gateway.security.JwtSecurityContextRepository;
import ru.bibank.gateway.web.JsonErrorWriter;

import java.time.Duration;
import java.util.List;

/**
 * Первый рубеж защиты: публичные и защищённые пути, роль ADMIN для /api/admin/**,
 * запрет внешнего доступа к межсервисному API /internal/**.
 * Сервисы повторно проверяют JWT и права доступа в своей зоне ответственности.
 */
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(
            ServerHttpSecurity http,
            JwtAuthenticationManager authenticationManager,
            JwtSecurityContextRepository securityContextRepository,
            ReactiveStringRedisTemplate redis,
            JsonErrorWriter errorWriter
    ) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .logout(ServerHttpSecurity.LogoutSpec::disable)
                .cors(cors -> {
                })
                .authenticationManager(authenticationManager)
                .securityContextRepository(securityContextRepository)
                .authorizeExchange(exchanges -> exchanges
                        .pathMatchers(HttpMethod.OPTIONS).permitAll()
                        .pathMatchers("/internal/**").denyAll()
                        .pathMatchers(HttpMethod.POST, "/api/auth/register", "/api/auth/login",
                                "/api/auth/refresh", "/api/auth/logout").permitAll()
                        .pathMatchers("/actuator/health/**", "/actuator/info").permitAll()
                        .pathMatchers("/swagger-ui.html", "/swagger-ui/**", "/webjars/**",
                                "/api-docs/**", "/gateway/v3/api-docs/**").permitAll()
                        .pathMatchers("/api/admin/**").hasAuthority("ROLE_ADMIN")
                        .pathMatchers("/api/**").hasAnyAuthority("ROLE_USER", "ROLE_ADMIN")
                        .anyExchange().denyAll()
                )
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((exchange, ex) -> errorWriter.write(exchange,
                                HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Требуется аутентификация"))
                        .accessDeniedHandler((exchange, ex) -> errorWriter.write(exchange,
                                HttpStatus.FORBIDDEN, "FORBIDDEN", "Недостаточно прав")))
                .addFilterAfter(new BlockedUserFilter(redis, errorWriter), SecurityWebFiltersOrder.AUTHORIZATION)
                .build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(GatewayProperties properties) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(properties.allowedOrigins());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "Idempotency-Key", "X-Request-Id"));
        config.setExposedHeaders(List.of("X-Request-Id", "X-RateLimit-Remaining"));
        config.setMaxAge(Duration.ofHours(1));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
