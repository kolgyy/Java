package backend.academy.linktracker.bot.command;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class UnknownCommand implements Command {

    @Override
    public String name() {
        return "unknown";
    }

    @Override
    public String description() {
        return "";
    }

    @Override
    public String execute(Long chatId, String[] args) {

        log.atInfo().addKeyValue("chatId", chatId).addKeyValue("args", args).log("Unknown command received");

        return "Неизвестная команда. Воспользуйтесь /help.";
    }
}
