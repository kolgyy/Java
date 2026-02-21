package backend.academy.linktracker.bot.command;

import backend.academy.linktracker.bot.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class StartCommand implements UserCommand {

    private final UserService userService;

    @Override
    public String name() {
        return "/start";
    }

    @Override
    public String description() {
        return "Начало работы";
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
        return "Добро пожаловать! Используйте /help, чтобы посмотреть доступные команды.";
    }
}
