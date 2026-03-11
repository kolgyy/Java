package backend.academy.linktracker.scrapper.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "bot.http")
public record BotHttpProperties(String baseUrl) {}
