package backend.academy.linktracker.bot.command;

import org.springframework.stereotype.Component;

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
        return "Неизвестная команда. Воспользуйтесь /help.";
    }
}
