package backend.academy.linktracker.bot.service;

import backend.academy.linktracker.bot.command.Command;
import backend.academy.linktracker.bot.command.CommandRegistry;
import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.request.SendMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class TelegramCommandService {

    private final CommandRegistry commandRegistry;
    private final TelegramBot telegramBot;

    public void handleUpdate(Update update) {
        if (update.message() == null || update.message().text() == null) return;

        String text = update.message().text();
        Long chatId = update.message().chat().id();
        String[] parts = text.split(" ", 2);

        log.atDebug()
            .addKeyValue("updateId", update.updateId())
            .addKeyValue("chatId",chatId)
            .addKeyValue("text", text)
            .log("Received Telegram update");

        Command command = commandRegistry.getCommand(parts[0]);
        String response = command.execute(chatId, parts);

        telegramBot.execute(new SendMessage(chatId.toString(), response));

        log.atDebug()
            .addKeyValue("chatId", chatId)
            .addKeyValue("command", command.getClass().getSimpleName())
            .addKeyValue("response", response)
            .log("Executed Telegram command and sent response");

    }

    public CommandRegistry getCommandRegistry() {
        return commandRegistry;
    }
}
