package backend.academy.linktracker.bot.command;

import backend.academy.linktracker.bot.configuration.BotCommandsProperties;
import backend.academy.linktracker.bot.dto.request.AddLinkRequest;
import backend.academy.linktracker.bot.dto.response.LinkResponse;
import backend.academy.linktracker.bot.dto.response.ListLinksResponse;
import backend.academy.linktracker.bot.model.UserSession;
import backend.academy.linktracker.bot.model.UserState;
import backend.academy.linktracker.bot.service.ScrapperService;
import backend.academy.linktracker.bot.service.UserSessionService;
import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class TrackCommand implements UserCommand {

    private final UserSessionService sessionService;
    private final ScrapperService scrapperService;
    private final BotCommandsProperties properties;

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
        String text = extractText(args);

        if (isCancel(text)) {
            sessionService.resetAll(chatId);
            return properties.track().cancelMessage();
        }

        return switch (session.getState()) {
            case IDLE -> startTracking(session);
            case AWAITING_LINK -> handleLink(chatId, session, text);
            case AWAITING_TAGS -> handleTags(chatId, session, text);
            default -> resetAndRestart(chatId);
        };
    }

    private String extractText(String[] args) {
        return (args != null && args.length > 0) ? String.join(" ", args).trim() : null;
    }

    private boolean isCancel(String text) {
        return text != null && text.equalsIgnoreCase(properties.track().cancelCommand());
    }

    private String startTracking(UserSession session) {
        session.setState(UserState.AWAITING_LINK);
        session.setCurrentCommand(name());
        sessionService.save(session);

        return properties.track().askLinkMessage();
    }

    private String handleLink(Long chatId, UserSession session, String text) {

        if (text == null || text.isBlank()) {
            return properties.track().askLinkMessage();
        }

        if (!isValidTrackableLink(text)) {
            return properties.track().invalidLinkMessage();
        }
        if (linkAlreadyTracked(chatId, text)) {
            sessionService.resetState(chatId);
            return properties.track().alreadyTrackedMessage();
        }

        session.setCurrentLink(text);
        session.setState(UserState.AWAITING_TAGS);
        sessionService.save(session);

        return properties.track().askTagsMessage();
    }

    private boolean linkAlreadyTracked(Long chatId, String url) {

        Optional<ListLinksResponse> existingLinksOpt = scrapperService.getLinks(chatId);

        return existingLinksOpt
                .map(r -> r.links().stream().anyMatch(l -> l.url().equals(url)))
                .orElse(false);
    }

    private String handleTags(Long chatId, UserSession session, String text) {

        List<String> tags = parseTags(text);

        AddLinkRequest request = new AddLinkRequest(session.getCurrentLink(), tags, List.of());

        Optional<LinkResponse> response = scrapperService.addLink(chatId, request);

        sessionService.resetState(chatId);

        return response.isPresent()
                ? properties.track().successMessage()
                : properties.track().alreadyTrackedMessage();
    }

    private List<String> parseTags(String text) {

        if (text == null || text.equalsIgnoreCase(properties.track().skipCommand())) {
            return List.of();
        }

        return Arrays.stream(text.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .toList();
    }

    private String resetAndRestart(Long chatId) {
        sessionService.resetState(chatId);
        return properties.track().askLinkMessage();
    }

    private boolean isValidTrackableLink(String url) {
        try {
            URI uri = URI.create(url);

            String host = uri.getHost();
            String path = uri.getPath();

            if (host == null || path == null) {
                return false;
            }
            if (host.equals("github.com")) {
                return path.matches("/[^/]+/[^/]+/?");
            }
            if (host.equals("stackoverflow.com")) {
                return path.matches("/questions/\\d+.*");
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }
}
