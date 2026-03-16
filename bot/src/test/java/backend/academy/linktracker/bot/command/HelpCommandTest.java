package backend.academy.linktracker.bot.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import backend.academy.linktracker.bot.configuration.BotCommandsProperties;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HelpCommandTest {

    private HelpCommand command;
    private BotCommandsProperties.Help props;
    private UserCommand cmd1;
    private UserCommand cmd2;

    @BeforeEach
    void setUp() {
        // Создаем фиктивные команды
        cmd1 = mock(UserCommand.class);
        when(cmd1.name()).thenReturn("/track");
        when(cmd1.description()).thenReturn("Добавить ссылку");

        cmd2 = mock(UserCommand.class);
        when(cmd2.name()).thenReturn("/untrack");
        when(cmd2.description()).thenReturn("Удалить ссылку");

        props = new BotCommandsProperties.Help("/help", "Справка по командам", "Доступные команды:");

        BotCommandsProperties properties = mock(BotCommandsProperties.class);
        when(properties.help()).thenReturn(props);

        command = new HelpCommand(List.of(cmd1, cmd2), properties);
    }

    @Test
    void execute_shouldReturnHelpMessageWithAllCommands() {
        long chatId = 1L;

        String result = command.execute(chatId, new String[] {});

        String expected = """
                Доступные команды:
                /track - Добавить ссылку
                /untrack - Удалить ссылку
                """;

        assertThat(result).isEqualTo(expected);
    }
}
