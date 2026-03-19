package backend.academy.linktracker.bot.configuration;

import backend.academy.linktracker.bot.properties.TelegramProperties;
import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.request.BaseRequest;
import com.pengrad.telegrambot.response.BaseResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@Slf4j
@TestConfiguration
public class MockTelegramConfiguration {

    @Bean
    @Primary
    public TelegramBot telegramBot(TelegramProperties properties) {
        log.atInfo().log("=== MOCK TelegramBot activated (tests) ===");

        return new TelegramBot("mock-token") {

            @Override
            public <T extends BaseRequest<T, R>, R extends BaseResponse> R execute(BaseRequest<T, R> request) {

                log.atDebug()
                        .addKeyValue("requestType", request.getClass().getSimpleName())
                        .addKeyValue("payload", request.getParameters())
                        .log("MOCK TelegramBot.execute called");

                return null;
            }
        };
    }
}
