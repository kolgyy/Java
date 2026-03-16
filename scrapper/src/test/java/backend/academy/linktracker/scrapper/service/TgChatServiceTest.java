package backend.academy.linktracker.scrapper.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import backend.academy.linktracker.scrapper.exception.ChatAlreadyRegisteredException;
import backend.academy.linktracker.scrapper.exception.ChatNotRegisteredException;
import backend.academy.linktracker.scrapper.model.TgChat;
import backend.academy.linktracker.scrapper.repository.TgChatRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TgChatServiceTest {

    @Mock
    private TgChatRepository repository;

    @InjectMocks
    private TgChatService service;

    @Test
    void registerChat_shouldSaveChat_whenChatNotExists() {
        Long chatId = 1L;

        when(repository.existsById(chatId)).thenReturn(false);

        TgChat result = service.registerChat(chatId);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(chatId);

        verify(repository).save(any(TgChat.class));
    }

    @Test
    void registerChat_shouldThrowException_whenChatAlreadyExists() {
        Long chatId = 1L;

        when(repository.existsById(chatId)).thenReturn(true);

        assertThatThrownBy(() -> service.registerChat(chatId)).isInstanceOf(ChatAlreadyRegisteredException.class);

        verify(repository, never()).save(any());
    }

    @Test
    void assertChatRegistered_shouldPass_whenChatExists() {
        Long chatId = 1L;

        when(repository.existsById(chatId)).thenReturn(true);

        assertThatCode(() -> service.assertChatRegistered(chatId)).doesNotThrowAnyException();
    }

    @Test
    void assertChatRegistered_shouldThrowException_whenChatNotExists() {
        Long chatId = 1L;

        when(repository.existsById(chatId)).thenReturn(false);

        assertThatThrownBy(() -> service.assertChatRegistered(chatId)).isInstanceOf(ChatNotRegisteredException.class);
    }

    @Test
    void removeChat_shouldDeleteChat_whenChatExists() {
        Long chatId = 1L;

        when(repository.existsById(chatId)).thenReturn(true);

        service.removeChat(chatId);

        verify(repository).delete(chatId);
    }

    @Test
    void removeChat_shouldThrowException_whenChatNotExists() {
        Long chatId = 1L;

        when(repository.existsById(chatId)).thenReturn(false);

        assertThatThrownBy(() -> service.removeChat(chatId)).isInstanceOf(ChatNotRegisteredException.class);

        verify(repository, never()).delete(any());
    }
}
