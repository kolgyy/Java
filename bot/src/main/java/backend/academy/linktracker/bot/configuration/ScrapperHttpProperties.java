package backend.academy.linktracker.bot.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "scrapper.http")
public record ScrapperHttpProperties(String baseUrl) {}
