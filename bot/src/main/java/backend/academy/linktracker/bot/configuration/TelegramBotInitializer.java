package backend.academy.linktracker.bot.configuration;

import backend.academy.linktracker.bot.service.TelegramCommandService;
import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.UpdatesListener;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class TelegramBotInitializer {

    private final TelegramBot telegramBot;
    private final TelegramCommandService commandService;

    @Bean
    public UpdatesListener telegramUpdatesListener() {
        UpdatesListener listener = updates -> {
            updates.forEach(commandService::handleUpdate);
            return UpdatesListener.CONFIRMED_UPDATES_ALL;
        };

        telegramBot.setUpdatesListener(listener);
        return listener;
    }
}
