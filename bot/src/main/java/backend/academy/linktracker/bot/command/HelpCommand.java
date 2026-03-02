package backend.academy.linktracker.bot.command;

import java.util.List;
import backend.academy.linktracker.bot.configuration.BotCommandsProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class HelpCommand implements UserCommand {

    private final List<UserCommand> commands;
    private final BotCommandsProperties properties;

    @Override
    public String name() {
        return properties.help().name();
    }

    @Override
    public String description() {
        return properties.help().description();
    }

    @Override
    public String execute(Long chatId, String[] args) {

        log.atInfo()
                .addKeyValue("chatId", chatId)
                .addKeyValue("command", name())
                .log("Executing help command");

        StringBuilder sb = new StringBuilder(properties.help().header() + "\n");

        for (Command command : commands) {
            sb.append(command.name())
                    .append(" - ")
                    .append(command.description())
                    .append("\n");
        }

        return sb.toString();
    }
}
