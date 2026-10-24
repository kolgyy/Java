package ru.bibank.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Учётная запись администратора, создаваемая при первом запуске.
 */
@ConfigurationProperties(prefix = "bibank.admin")
public record AdminProperties(String email, String password, String fullName) {
}
