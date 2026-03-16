package backend.academy.linktracker.bot.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import backend.academy.linktracker.bot.configuration.BotCommandsProperties;
import backend.academy.linktracker.bot.dto.request.RemoveLinkRequest;
import backend.academy.linktracker.bot.dto.response.LinkResponse;
import backend.academy.linktracker.bot.model.UserSession;
import backend.academy.linktracker.bot.model.UserState;
import backend.academy.linktracker.bot.service.ScrapperService;
import backend.academy.linktracker.bot.service.UserSessionService;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class UntrackCommandTest {

    private UserSessionService sessionService;
    private ScrapperService scrapperService;
    private UntrackCommand command;
    private BotCommandsProperties.Untrack props;

    @BeforeEach
    void setUp() {
        sessionService = mock(UserSessionService.class);
        scrapperService = mock(ScrapperService.class);

        props = new BotCommandsProperties.Untrack(
                "/untrack",
                "Удалить ссылку",
                "/cancel",
                "Процесс удаления отменён.",
                "Введите ссылку для удаления:",
                "Ссылка успешно удалена.",
                "Ссылка не найдена.");

        BotCommandsProperties properties = mock(BotCommandsProperties.class);
        when(properties.untrack()).thenReturn(props);

        command = new UntrackCommand(sessionService, scrapperService, properties);
    }

    @Test
    void execute_shouldStartUntrack_whenIdle() {
        long chatId = 1L;
        UserSession session = new UserSession(chatId);
        session.setState(UserState.IDLE);

        when(sessionService.getSession(chatId)).thenReturn(session);

        String result = command.execute(chatId, new String[] {});

        assertThat(result).isEqualTo(props.askLinkMessage());
        assertThat(session.getState()).isEqualTo(UserState.AWAITING_LINK);
        assertThat(session.getCurrentCommand()).isEqualTo(command.name());
        verify(sessionService).save(session);
    }

    @Test
    void execute_shouldStartUntrack_whenArgsNull() {
        long chatId = 1L;
        UserSession session = new UserSession(chatId);
        session.setState(UserState.IDLE);

        when(sessionService.getSession(chatId)).thenReturn(session);

        String result = command.execute(chatId, null);

        assertThat(result).isEqualTo(props.askLinkMessage());
        assertThat(session.getState()).isEqualTo(UserState.AWAITING_LINK);
        verify(sessionService).save(session);
    }

    @Test
    void execute_shouldRemoveLinkSuccessfully() {
        long chatId = 1L;
        String url = "https://github.com/user/repo";

        UserSession session = new UserSession(chatId);
        session.setState(UserState.AWAITING_LINK);

        when(sessionService.getSession(chatId)).thenReturn(session);
        when(scrapperService.removeLink(eq(chatId), any(RemoveLinkRequest.class)))
                .thenReturn(Optional.of(mock(LinkResponse.class)));

        String result = command.execute(chatId, new String[] {url});

        assertThat(result).isEqualTo(props.successMessage());
        verify(sessionService).resetState(chatId);
    }

    @Test
    void execute_shouldReturnNotFound_whenLinkMissing() {
        long chatId = 1L;
        String url = "https://github.com/user/repo";

        UserSession session = new UserSession(chatId);
        session.setState(UserState.AWAITING_LINK);

        when(sessionService.getSession(chatId)).thenReturn(session);
        when(scrapperService.removeLink(eq(chatId), any(RemoveLinkRequest.class)))
                .thenReturn(Optional.empty());

        String result = command.execute(chatId, new String[] {url});

        assertThat(result).isEqualTo(props.linkNotFoundMessage());
        verify(sessionService).resetState(chatId);
    }

    @Test
    void execute_shouldCancelProcess() {
        long chatId = 1L;

        UserSession session = new UserSession(chatId);
        session.setState(UserState.AWAITING_LINK);

        when(sessionService.getSession(chatId)).thenReturn(session);

        String result = command.execute(chatId, new String[] {"/cancel"});

        assertThat(result).isEqualTo(props.cancelMessage());
        verify(sessionService).resetAll(chatId);
    }

    @Test
    void execute_shouldCancelProcess_withTrailingSpaces() {
        long chatId = 1L;

        UserSession session = new UserSession(chatId);
        session.setState(UserState.AWAITING_LINK);

        when(sessionService.getSession(chatId)).thenReturn(session);

        String result = command.execute(chatId, new String[] {"/cancel "}); // Пробел в конце

        assertThat(result).isEqualTo(props.cancelMessage());
        verify(sessionService).resetAll(chatId);
    }

    @Test
    void execute_shouldAskLinkAgain_whenInputEmpty() {
        long chatId = 1L;

        UserSession session = new UserSession(chatId);
        session.setState(UserState.AWAITING_LINK);

        when(sessionService.getSession(chatId)).thenReturn(session);

        String result = command.execute(chatId, new String[] {""});

        assertThat(result).isEqualTo(props.askLinkMessage());
    }

    @Test
    void execute_shouldResetAndRestart_whenUnexpectedState() {
        long chatId = 1L;

        UserSession session = new UserSession(chatId);
        session.setState(UserState.AWAITING_TAGS); // Любое неожиданное состояние

        when(sessionService.getSession(chatId)).thenReturn(session);

        String result = command.execute(chatId, new String[] {"random input"});

        assertThat(result).isEqualTo(props.askLinkMessage());
        verify(sessionService).resetState(chatId);
    }
}
