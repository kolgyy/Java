package backend.academy.linktracker.bot.service;

import backend.academy.linktracker.bot.command.Command;
import backend.academy.linktracker.bot.command.CommandRegistry;
import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.request.SendMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

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

        Command command = commandRegistry.getCommand(parts[0]);
        String response = command.execute(chatId, parts);

        telegramBot.execute(new SendMessage(chatId.toString(), response));

    }

    public CommandRegistry getCommandRegistry() {
        return commandRegistry;
    }
}
