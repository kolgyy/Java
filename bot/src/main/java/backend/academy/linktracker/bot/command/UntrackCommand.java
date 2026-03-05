package backend.academy.linktracker.bot.command;

import backend.academy.linktracker.bot.configuration.BotCommandsProperties;
import backend.academy.linktracker.bot.model.UserSession;
import backend.academy.linktracker.bot.model.UserState;
import backend.academy.linktracker.bot.service.LinkService;
import backend.academy.linktracker.bot.service.UserSessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class UntrackCommand implements UserCommand {

    private final UserSessionService sessionService;
    private final LinkService linkService;
    private final BotCommandsProperties properties;

    @Override
    public String name() {
        return properties.untrack().name();
    }

    @Override
    public String description() {
        return properties.untrack().description();
    }

    @Override
    public String execute(Long chatId, String[] args) {
        UserSession session = sessionService.getSession(chatId);
        String text = args.length > 1 ? args[1].trim() : null;

        if (text != null && text.equalsIgnoreCase(properties.untrack().cancelCommand())) {
            sessionService.resetAll(chatId);
            return properties.untrack().cancelMessage();
        }

        switch (session.getState()) {
            case IDLE -> {
                session.setState(UserState.AWAITING_LINK);
                sessionService.save(session);
                return properties.untrack().askLinkMessage();
            }

            case AWAITING_LINK -> {
                if (text == null || text.isBlank()) {
                    return properties.untrack().askLinkMessage();
                }

                boolean removed = linkService.removeLink(chatId, text);
                sessionService.resetState(chatId);

                if (removed) {
                    return properties.untrack().successMessage();
                } else {
                    return properties.untrack().linkNotFoundMessage();
                }
            }

            default -> {
                sessionService.resetState(chatId);
                return properties.untrack().askLinkMessage();
            }
        }
    }
}
