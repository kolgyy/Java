package backend.academy.linktracker.bot.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import backend.academy.linktracker.bot.model.UserSession;
import backend.academy.linktracker.bot.repository.UserSessionRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserSessionServiceTest {

    @Mock
    private UserSessionRepository repository;

    @InjectMocks
    private UserSessionService service;

    @Test
    void getSession_shouldReturnExistingSession() {
        long chatId = 1L;
        UserSession existing = new UserSession(chatId);
        when(repository.findByChatId(chatId)).thenReturn(Optional.of(existing));

        UserSession result = service.getSession(chatId);

        assertThat(result).isEqualTo(existing);
        verify(repository, never()).save(any());
    }

    @Test
    void getSession_shouldCreateAndSaveNewSession_whenNotExists() {
        long chatId = 1L;
        when(repository.findByChatId(chatId)).thenReturn(Optional.empty());

        ArgumentCaptor<UserSession> captor = ArgumentCaptor.forClass(UserSession.class);
        when(repository.save(captor.capture())).thenAnswer(invocation -> invocation.getArgument(0));

        UserSession result = service.getSession(chatId);

        assertThat(result.getChatId()).isEqualTo(chatId);
        assertThat(result).isSameAs(captor.getValue());
        verify(repository).save(any(UserSession.class));
    }

    @Test
    void resetState_shouldResetAndSaveSession() {
        long chatId = 1L;
        UserSession session = spy(new UserSession(chatId));
        when(repository.findByChatId(chatId)).thenReturn(Optional.of(session));
        when(repository.save(session)).thenReturn(session);

        service.resetState(chatId);

        verify(session).resetState();
        verify(repository).save(session);
    }

    @Test
    void resetAll_shouldResetAllAndSaveSession() {
        long chatId = 1L;
        UserSession session = spy(new UserSession(chatId));
        when(repository.findByChatId(chatId)).thenReturn(Optional.of(session));
        when(repository.save(session)).thenReturn(session);

        service.resetAll(chatId);

        verify(session).resetAll();
        verify(repository).save(session);
    }

    @Test
    void save_shouldDelegateToRepository() {
        UserSession session = new UserSession(1L);
        when(repository.save(session)).thenReturn(session);

        UserSession result = service.save(session);

        assertThat(result).isSameAs(session);
        verify(repository).save(session);
    }
}
