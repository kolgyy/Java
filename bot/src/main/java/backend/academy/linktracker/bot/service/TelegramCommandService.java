package backend.academy.linktracker.bot.service;

import backend.academy.linktracker.bot.command.Command;
import backend.academy.linktracker.bot.command.CommandRegistry;
import backend.academy.linktracker.bot.model.UserSession;
import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.request.SendMessage;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class TelegramCommandService {

    @Getter
    private final CommandRegistry commandRegistry;

    private final TelegramBot telegramBot;
    private final UserSessionService sessionService;

    public void handleUpdate(Update update) {
        if (update.message() == null || update.message().text() == null) return;

        Long chatId = update.message().chat().id();
        String text = update.message().text().trim();
        String[] parts = text.split("\\s+", 2);

        UserSession session = sessionService.getSession(chatId);

        Command command;

        if (commandRegistry.hasCommand(parts[0])) {
            sessionService.resetAll(chatId);
            command = commandRegistry.getCommand(parts[0]);
        } else if (session.getCurrentCommand() != null) {
            command = commandRegistry.getCommand(session.getCurrentCommand());
        } else {
            command = commandRegistry.getCommand(parts[0]);
        }

        String response = command.execute(chatId, parts);

        telegramBot.execute(new SendMessage(chatId.toString(), response));

        log.atDebug()
                .addKeyValue("chatId", chatId)
                .addKeyValue("command", command.getClass().getSimpleName())
                .addKeyValue("response", response)
                .log("Executed Telegram command and sent response");
    }
}
