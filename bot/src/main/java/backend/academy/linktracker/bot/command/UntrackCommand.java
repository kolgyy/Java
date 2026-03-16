package backend.academy.linktracker.bot.command;

import backend.academy.linktracker.bot.configuration.BotCommandsProperties;
import backend.academy.linktracker.bot.dto.request.RemoveLinkRequest;
import backend.academy.linktracker.bot.model.UserSession;
import backend.academy.linktracker.bot.model.UserState;
import backend.academy.linktracker.bot.service.ScrapperService;
import backend.academy.linktracker.bot.service.UserSessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class UntrackCommand implements UserCommand {

    private final UserSessionService sessionService;
    private final ScrapperService scrapperService;
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
        String text = extractText(args);

        if (isCancel(text)) {
            sessionService.resetAll(chatId);
            return properties.untrack().cancelMessage();
        }

        return switch (session.getState()) {
            case IDLE -> startUntrack(session);
            case AWAITING_LINK -> handleLink(chatId, text);
            default -> resetAndRestart(chatId);
        };
    }

    private String extractText(String[] args) {
        return (args != null && args.length > 0) ? String.join(" ", args).trim() : null;
    }

    private boolean isCancel(String text) {
        return text != null && text.trim().equalsIgnoreCase(properties.untrack().cancelCommand());
    }

    private String startUntrack(UserSession session) {
        session.setState(UserState.AWAITING_LINK);
        session.setCurrentCommand(name());
        sessionService.save(session);

        return properties.untrack().askLinkMessage();
    }

    private String handleLink(Long chatId, String text) {

        if (text == null || text.isBlank()) {
            return properties.untrack().askLinkMessage();
        }

        log.atInfo().addKeyValue("chatId", chatId).addKeyValue("url", text).log("User attempts to untrack link");

        RemoveLinkRequest request = new RemoveLinkRequest(text);

        boolean removed = scrapperService.removeLink(chatId, request).isPresent();

        sessionService.resetState(chatId);

        return removed
                ? properties.untrack().successMessage()
                : properties.untrack().linkNotFoundMessage();
    }

    private String resetAndRestart(Long chatId) {
        sessionService.resetState(chatId);
        return properties.untrack().askLinkMessage();
    }
}
