package backend.academy.linktracker.bot.command;

import backend.academy.linktracker.bot.configuration.BotCommandsProperties;
import backend.academy.linktracker.bot.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class StartCommand implements UserCommand {

    private final UserService userService;
    private final BotCommandsProperties properties;

    @Override
    public String name() {
        return properties.start().name();
    }

    @Override
    public String description() {
        return properties.start().description();
    }

    @Override
    public String execute(Long chatId, String[] args) {

        log.atInfo()
                .addKeyValue("chatId", chatId)
                .addKeyValue("command", name())
                .log("Executing start command");

        if (!userService.isRegistered(chatId)) {
            userService.register(chatId);
        }
        return properties.start().message();
    }
}
