package backend.academy.linktracker.bot.service;

import backend.academy.linktracker.bot.configuration.BotMessagesProperties;
import backend.academy.linktracker.bot.dto.LinkUpdate;
import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.request.SendMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class UpdateService {

    private final TelegramBot telegramBot;
    private final BotMessagesProperties messages;

    public void processUpdate(LinkUpdate update) {

        log.atInfo()
                .addKeyValue("url", update.url())
                .addKeyValue("chatCount", update.tgChatIds().size())
                .log("Processing link update");

        for (Long chatId : update.tgChatIds()) {

            String text = String.format(messages.linkUpdated(), update.url(), update.description());

            log.atInfo()
                    .addKeyValue("chatId", chatId)
                    .addKeyValue("url", update.url())
                    .log("Sending update notification");

            telegramBot.execute(new SendMessage(chatId.toString(), text));
        }
    }
}
