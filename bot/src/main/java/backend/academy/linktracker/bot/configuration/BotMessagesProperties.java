package backend.academy.linktracker.bot.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "bot.messages")
public record BotMessagesProperties(String linkUpdated) {}
