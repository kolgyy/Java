package backend.academy.linktracker.bot.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import backend.academy.linktracker.bot.configuration.BotCommandsProperties;
import backend.academy.linktracker.bot.service.ScrapperService;
import backend.academy.linktracker.bot.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class StartCommandTest {

    private ScrapperService scrapperService;
    private UserService userService;
    private StartCommand command;
    private BotCommandsProperties.Start props;

    @BeforeEach
    void setUp() {
        scrapperService = mock(ScrapperService.class);
        userService = mock(UserService.class);

        props = new BotCommandsProperties.Start(
                "/start", "Начало работы", "Добро пожаловать! Используйте /help, чтобы посмотреть доступные команды.");

        BotCommandsProperties properties = mock(BotCommandsProperties.class);
        when(properties.start()).thenReturn(props);

        command = new StartCommand(scrapperService, userService, properties);
    }

    @Test
    void execute_shouldRegisterNewUser_whenNotRegistered() {
        long chatId = 1L;

        when(userService.isRegistered(chatId)).thenReturn(false);

        String result = command.execute(chatId, new String[] {});

        assertThat(result).isEqualTo(props.message());
        verify(userService).register(chatId);
        verify(scrapperService).registerChat(chatId);
    }

    @Test
    void execute_shouldNotRegisterUser_whenAlreadyRegistered() {
        long chatId = 1L;

        when(userService.isRegistered(chatId)).thenReturn(true);

        String result = command.execute(chatId, new String[] {});

        assertThat(result).isEqualTo(props.message());
        verify(userService, never()).register(chatId);
        verify(scrapperService, never()).registerChat(chatId);
    }
}
