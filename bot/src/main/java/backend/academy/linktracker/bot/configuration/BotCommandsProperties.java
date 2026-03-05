package backend.academy.linktracker.bot.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "bot.commands")
public record BotCommandsProperties(Start start, Help help, Unknown unknown, Track track) {
    public record Start(String name, String description, String message) {}

    public record Help(String name, String description, String header) {}

    public record Track(String name, String description, String askLinkMessage, String alreadyTrackedMessage, String askTagsMessage, String successMessage, String cancelMessage, String skipCommand, String cancelCommand) {}

    public record Unknown(String name, String description, String message) {}
}
