package ru.bibank.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * @param jwtSecret      ключ проверки подписи JWT
 * @param allowedOrigins разрешённые источники CORS
 * @param services       адреса микросервисов
 */
@ConfigurationProperties(prefix = "bibank.gateway")
public record GatewayProperties(String jwtSecret, List<String> allowedOrigins, Services services) {

    public record Services(String auth, String account, String payment, String loan, String notification) {
    }
}
