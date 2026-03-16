package backend.academy.linktracker.bot.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import backend.academy.linktracker.bot.configuration.BotCommandsProperties;
import backend.academy.linktracker.bot.dto.request.AddLinkRequest;
import backend.academy.linktracker.bot.dto.response.LinkResponse;
import backend.academy.linktracker.bot.dto.response.ListLinksResponse;
import backend.academy.linktracker.bot.model.UserSession;
import backend.academy.linktracker.bot.model.UserState;
import backend.academy.linktracker.bot.service.ScrapperService;
import backend.academy.linktracker.bot.service.UserSessionService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TrackCommandTest {

    private UserSessionService sessionService;
    private ScrapperService scrapperService;
    private TrackCommand command;
    private BotCommandsProperties.Track trackProps;

    @BeforeEach
    void setUp() {
        sessionService = mock(UserSessionService.class);
        scrapperService = mock(ScrapperService.class);

        trackProps = new BotCommandsProperties.Track(
                "/track",
                "Добавить ссылку",
                "Введите ссылку:",
                "Некорректная ссылка. Поддерживаются только GitHub и StackOverflow.",
                "Ссылка уже отслеживается.",
                "Введите теги через запятую (или /skip):",
                "Ссылка успешно добавлена.",
                "Процесс отслеживания отменён.",
                "/skip",
                "/cancel");

        BotCommandsProperties properties = mock(BotCommandsProperties.class);
        when(properties.track()).thenReturn(trackProps);

        command = new TrackCommand(sessionService, scrapperService, properties);
    }

    @Test
    void execute_shouldStartTracking_whenIdle() {
        long chatId = 1L;
        UserSession session = new UserSession(chatId);
        session.setState(UserState.IDLE);

        when(sessionService.getSession(chatId)).thenReturn(session);
        when(sessionService.save(session)).thenReturn(session);

        String result = command.execute(chatId, new String[] {});

        assertThat(result).isEqualTo(trackProps.askLinkMessage());
        assertThat(session.getState()).isEqualTo(UserState.AWAITING_LINK);
        assertThat(session.getCurrentCommand()).isEqualTo(command.name());
        verify(sessionService).save(session);
    }

    @Test
    void execute_shouldAskTags_whenValidLink() {
        long chatId = 1L;
        String url = "https://github.com/user/repo";
        UserSession session = new UserSession(chatId);
        session.setState(UserState.AWAITING_LINK);

        when(sessionService.getSession(chatId)).thenReturn(session);
        when(scrapperService.getLinks(chatId)).thenReturn(Optional.of(new ListLinksResponse(List.of(), 0)));
        when(sessionService.save(session)).thenReturn(session);

        String result = command.execute(chatId, new String[] {url});

        assertThat(result).isEqualTo(trackProps.askTagsMessage());
        assertThat(session.getState()).isEqualTo(UserState.AWAITING_TAGS);
        assertThat(session.getCurrentLink()).isEqualTo(url);
        verify(sessionService).save(session);
    }

    @Test
    void execute_shouldReturnAlreadyTracked_whenLinkExists() {
        long chatId = 1L;
        String url = "https://github.com/user/repo";
        UserSession session = new UserSession(chatId);
        session.setState(UserState.AWAITING_LINK);

        ListLinksResponse existing = new ListLinksResponse(List.of(new LinkResponse(1L, url, List.of(), List.of())), 1);

        when(sessionService.getSession(chatId)).thenReturn(session);
        when(scrapperService.getLinks(chatId)).thenReturn(Optional.of(existing));

        String result = command.execute(chatId, new String[] {url});

        assertThat(result).isEqualTo(trackProps.alreadyTrackedMessage());
        verify(sessionService).resetState(chatId);
    }

    @Test
    void execute_shouldHandleTags_andReturnSuccess() {
        long chatId = 1L;
        String url = "https://github.com/user/repo";
        UserSession session = new UserSession(chatId);
        session.setState(UserState.AWAITING_TAGS);
        session.setCurrentLink(url);

        when(sessionService.getSession(chatId)).thenReturn(session);
        when(scrapperService.addLink(eq(chatId), any(AddLinkRequest.class)))
                .thenReturn(Optional.of(new LinkResponse(1L, url, List.of("tag1"), List.of())));

        String result = command.execute(chatId, new String[] {"tag1"});

        assertThat(result).isEqualTo(trackProps.successMessage());
        verify(sessionService).resetState(chatId);
    }

    @Test
    void execute_shouldSkipTags_whenSkipCommand() {
        long chatId = 1L;
        String url = "https://github.com/user/repo";
        UserSession session = new UserSession(chatId);
        session.setState(UserState.AWAITING_TAGS);
        session.setCurrentLink(url);

        when(sessionService.getSession(chatId)).thenReturn(session);
        when(scrapperService.addLink(eq(chatId), any(AddLinkRequest.class)))
                .thenReturn(Optional.of(new LinkResponse(1L, url, List.of(), List.of())));

        String result = command.execute(chatId, new String[] {"/skip"});

        assertThat(result).isEqualTo(trackProps.successMessage());
        verify(sessionService).resetState(chatId);
    }

    @Test
    void execute_shouldCancelProcess() {
        long chatId = 1L;
        UserSession session = new UserSession(chatId);
        session.setState(UserState.AWAITING_LINK);

        when(sessionService.getSession(chatId)).thenReturn(session);

        String result = command.execute(chatId, new String[] {"/cancel"});

        assertThat(result).isEqualTo(trackProps.cancelMessage());
        verify(sessionService).resetAll(chatId);
    }

    @Test
    void execute_shouldReturnInvalidLinkMessage_whenInvalidUrl() {
        long chatId = 1L;
        String url = "invalid-url";
        UserSession session = new UserSession(chatId);
        session.setState(UserState.AWAITING_LINK);

        when(sessionService.getSession(chatId)).thenReturn(session);

        String result = command.execute(chatId, new String[] {url});

        assertThat(result).isEqualTo(trackProps.invalidLinkMessage());
    }

    @Test
    void execute_shouldReturnAlreadyTracked_whenAddLinkFails() {
        long chatId = 1L;
        String url = "https://github.com/user/repo";
        UserSession session = new UserSession(chatId);
        session.setState(UserState.AWAITING_TAGS);
        session.setCurrentLink(url);

        when(sessionService.getSession(chatId)).thenReturn(session);
        // Симулируем, что добавление ссылки вернуло пустой Optional
        when(scrapperService.addLink(eq(chatId), any(AddLinkRequest.class))).thenReturn(Optional.empty());

        String result = command.execute(chatId, new String[] {"tag1"});

        assertThat(result).isEqualTo(trackProps.alreadyTrackedMessage());
        verify(sessionService).resetState(chatId);
    }
}
