package backend.academy.linktracker.bot.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "scrapper.grpc")
public record ScrapperGrpcProperties(String baseUrl, String host, Integer port) {}
