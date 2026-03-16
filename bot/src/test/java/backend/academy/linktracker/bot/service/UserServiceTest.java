package backend.academy.linktracker.bot.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import backend.academy.linktracker.bot.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserService userService;

    @Test
    void isRegistered_shouldReturnTrue_whenUserExists() {
        long chatId = 1L;
        when(userRepository.existsByChatId(chatId)).thenReturn(true);

        boolean result = userService.isRegistered(chatId);

        assertThat(result).isTrue();
        verify(userRepository).existsByChatId(chatId);
    }

    @Test
    void isRegistered_shouldReturnFalse_whenUserDoesNotExist() {
        long chatId = 1L;
        when(userRepository.existsByChatId(chatId)).thenReturn(false);

        boolean result = userService.isRegistered(chatId);

        assertThat(result).isFalse();
        verify(userRepository).existsByChatId(chatId);
    }

    @Test
    void register_shouldSaveUser_whenNotExists() {
        long chatId = 1L;
        when(userRepository.existsByChatId(chatId)).thenReturn(false);

        userService.register(chatId);

        verify(userRepository).save(argThat(user -> user.getChatId() == chatId && user.isRegistered()));
    }

    @Test
    void register_shouldNotSaveUser_whenAlreadyExists() {
        long chatId = 1L;
        when(userRepository.existsByChatId(chatId)).thenReturn(true);

        userService.register(chatId);

        verify(userRepository, never()).save(any());
    }
}
