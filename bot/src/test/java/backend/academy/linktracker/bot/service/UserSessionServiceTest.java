package backend.academy.linktracker.bot.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import backend.academy.linktracker.bot.model.UserSession;
import backend.academy.linktracker.bot.model.UserState;
import backend.academy.linktracker.bot.repository.UserSessionRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class UserSessionServiceTest {

    private UserSessionRepository repository;
    private UserSessionService service;

    @BeforeEach
    void setUp() {
        repository = mock(UserSessionRepository.class);
        service = new UserSessionService(repository);
    }

    @Test
    void shouldReturnExistingSession() {

        Long chatId = 1L;

        UserSession session = new UserSession(chatId);

        when(repository.findByChatId(chatId)).thenReturn(Optional.of(session));

        UserSession result = service.getSession(chatId);

        assertThat(result).isEqualTo(session);

        verify(repository, never()).save(any());
    }

    @Test
    void shouldCreateSessionIfNotExists() {

        Long chatId = 2L;

        when(repository.findByChatId(chatId)).thenReturn(Optional.empty());

        when(repository.save(any(UserSession.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserSession result = service.getSession(chatId);

        assertThat(result.getChatId()).isEqualTo(chatId);

        verify(repository).save(any(UserSession.class));
    }

    @Test
    void shouldResetState() {

        Long chatId = 3L;

        UserSession session = new UserSession(chatId);
        session.setState(UserState.AWAITING_LINK);
        session.setCurrentCommand("/track");

        when(repository.findByChatId(chatId)).thenReturn(Optional.of(session));

        when(repository.save(any(UserSession.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.resetState(chatId);

        assertThat(session.getState()).isEqualTo(UserState.IDLE);
        assertThat(session.getCurrentCommand()).isNull();

        verify(repository).save(session);
    }

    @Test
    void shouldResetAll() {

        Long chatId = 4L;

        UserSession session = new UserSession(chatId);
        session.setState(UserState.AWAITING_LINK);
        session.setCurrentCommand("/track");
        session.setCurrentLink("https://github.com/test/repo");
        session.setCurrentTags("java");

        when(repository.findByChatId(chatId)).thenReturn(Optional.of(session));

        when(repository.save(any(UserSession.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.resetAll(chatId);

        assertThat(session.getState()).isEqualTo(UserState.IDLE);
        assertThat(session.getCurrentCommand()).isNull();
        assertThat(session.getCurrentLink()).isNull();
        assertThat(session.getCurrentTags()).isNull();

        verify(repository).save(session);
    }

    @Test
    void shouldSaveSession() {

        UserSession session = new UserSession(5L);

        when(repository.save(session)).thenReturn(session);

        UserSession result = service.save(session);

        assertThat(result).isEqualTo(session);

        verify(repository).save(session);
    }
}
