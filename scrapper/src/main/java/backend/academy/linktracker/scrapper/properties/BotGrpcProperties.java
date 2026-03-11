package backend.academy.linktracker.scrapper.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "bot.grpc")
public record BotGrpcProperties(String host, Integer port) {}
