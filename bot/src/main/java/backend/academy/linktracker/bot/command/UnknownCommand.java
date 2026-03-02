package backend.academy.linktracker.bot.command;

import backend.academy.linktracker.bot.configuration.BotCommandsProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class UnknownCommand implements Command {

    private final BotCommandsProperties properties;

    @Override
    public String name() {
        return properties.unknown().name();
    }

    @Override
    public String description() {
        return properties.unknown().description();
    }

    @Override
    public String execute(Long chatId, String[] args) {

        log.atInfo().addKeyValue("chatId", chatId).addKeyValue("args", args).log("Unknown command received");

        return properties.unknown().message();
    }
}

