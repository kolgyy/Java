package backend.academy.linktracker.bot.command;

import backend.academy.linktracker.bot.configuration.BotCommandsProperties;
import backend.academy.linktracker.bot.model.UserSession;
import backend.academy.linktracker.bot.service.LinkService;
import backend.academy.linktracker.bot.service.UserSessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import java.util.Arrays;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class TrackCommand implements UserCommand {

    private final UserSessionService sessionService;
    private final BotCommandsProperties properties;
    private final LinkService linkService;

    @Override
    public String name() {
        return properties.track().name();
    }

    @Override
    public String description() {
        return properties.track().description();
    }

    @Override
    public String execute(Long chatId, String[] args) {
        UserSession session = sessionService.getSession(chatId);
        String text = args.length > 1 ? args[1].trim() : null;

        if (text != null && text.equalsIgnoreCase(properties.track().cancelCommand())) {
            sessionService.resetAll(chatId);
            return properties.track().cancelMessage();
        }

        switch (session.getState()) {
            case IDLE -> {
                sessionService.startTracking(chatId);
                return properties.track().askLinkMessage();
            }

            case AWAITING_LINK -> {
                if (text == null || text.isBlank()) {
                    return properties.track().askLinkMessage();
                }
                sessionService.setLink(chatId, text);
                return properties.track().askTagsMessage();
            }

            case AWAITING_TAGS -> {
                List<String> tags = List.of();
                if (text != null && !text.equalsIgnoreCase(properties.track().skipCommand())) {
                    tags = Arrays.stream(text.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isBlank())
                        .toList();
                }

                boolean added = linkService.addLink(chatId, session.getCurrentLink(), tags).isPresent();
                sessionService.resetState(chatId);

                return added ? properties.track().successMessage() : properties.track().alreadyTrackedMessage();
            }

            default -> {
                sessionService.resetState(chatId);
                return properties.track().askLinkMessage();
            }
        }
    }
}
