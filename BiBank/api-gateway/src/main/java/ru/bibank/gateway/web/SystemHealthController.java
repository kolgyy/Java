package ru.bibank.gateway.web;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ru.bibank.gateway.config.GatewayProperties;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Сводка состояния сервисов для административной панели (только ADMIN, см. SecurityConfig).
 */
@RestController
public class SystemHealthController {

    public record ServiceHealth(String name, String status, long latencyMs, Object components) {
    }

    private final WebClient webClient;
    private final GatewayProperties properties;

    public SystemHealthController(WebClient.Builder builder, GatewayProperties properties) {
        this.webClient = builder.build();
        this.properties = properties;
    }

    @GetMapping("/api/admin/system/health")
    public Mono<Map<String, Object>> health() {
        var s = properties.services();
        Map<String, String> services = new LinkedHashMap<>();
        services.put("auth-service", s.auth());
        services.put("account-service", s.account());
        services.put("payment-service", s.payment());
        services.put("loan-service", s.loan());
        services.put("notification-service", s.notification());

        return Flux.fromIterable(services.entrySet())
                .flatMapSequential(e -> check(e.getKey(), e.getValue()))
                .collectList()
                .map(list -> {
                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put("status", list.stream().allMatch(h -> "UP".equals(h.status())) ? "UP" : "DEGRADED");
                    result.put("checkedAt", Instant.now().toString());
                    result.put("gateway", "UP");
                    result.put("services", list);
                    return result;
                });
    }

    private Mono<ServiceHealth> check(String name, String baseUrl) {
        long start = System.nanoTime();
        return webClient.get().uri(baseUrl + "/actuator/health")
                .exchangeToMono(r -> r.bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {
                }))
                .timeout(Duration.ofSeconds(3))
                .map(body -> new ServiceHealth(name, String.valueOf(body.getOrDefault("status", "UNKNOWN")),
                        (System.nanoTime() - start) / 1_000_000, body.get("components")))
                .onErrorResume(e -> Mono.just(new ServiceHealth(name, "DOWN",
                        (System.nanoTime() - start) / 1_000_000, List.of())));
    }
}
