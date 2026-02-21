package backend.academy.linktracker.bot.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import backend.academy.linktracker.bot.command.CommandRegistry;
import backend.academy.linktracker.bot.command.HelpCommand;
import backend.academy.linktracker.bot.command.StartCommand;
import backend.academy.linktracker.bot.command.UnknownCommand;
import backend.academy.linktracker.bot.repository.UserRepository;
import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.model.Chat;
import com.pengrad.telegrambot.model.Message;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.request.SendMessage;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class TelegramCommandServiceTest {

    private TelegramBot telegramBot;
    private CommandRegistry commandRegistry;
    private TelegramCommandService service;

    @BeforeEach
    void setUp() {
        telegramBot = mock(TelegramBot.class);

        commandRegistry = new CommandRegistry(
            List.of(
                new StartCommand(
                    new UserService(mock(UserRepository.class))
                ),
                new HelpCommand(List.of())
            ),
            new UnknownCommand()
        );

        service = new TelegramCommandService(commandRegistry, telegramBot);
    }

    @Test
    void testStartCommand() {

        // Arrange
        Long chatId = 123L;

        Update update = mock(Update.class);
        Message message = mock(Message.class);
        Chat chat = mock(Chat.class);

        when(update.message()).thenReturn(message);
        when(message.text()).thenReturn("/start");
        when(message.chat()).thenReturn(chat);
        when(chat.id()).thenReturn(chatId);

        // Act
        service.handleUpdate(update);

        // Assert
        ArgumentCaptor<SendMessage> captor = ArgumentCaptor.forClass(SendMessage.class);
        verify(telegramBot, times(1)).execute(captor.capture());

        SendMessage sent = captor.getValue();
        String text = (String) sent.getParameters().get("text");
        String chatIdSent = (String) sent.getParameters().get("chat_id");

        assertThat(text)
            .isNotNull()
            .isEqualTo("Добро пожаловать! Используйте /help, чтобы посмотреть доступные команды.");

        assertThat(chatIdSent).isEqualTo(chatId.toString());
    }

    @Test
    void testHelpCommand() {

        // Arrange
        Long chatId = 456L;

        Update update = mock(Update.class);
        Message message = mock(Message.class);
        Chat chat = mock(Chat.class);

        when(update.message()).thenReturn(message);
        when(message.text()).thenReturn("/help");
        when(message.chat()).thenReturn(chat);
        when(chat.id()).thenReturn(chatId);

        // Act
        service.handleUpdate(update);

        // Assert
        ArgumentCaptor<SendMessage> captor = ArgumentCaptor.forClass(SendMessage.class);
        verify(telegramBot).execute(captor.capture());

        SendMessage sent = captor.getValue();
        String text = (String) sent.getParameters().get("text");
        String chatIdSent = (String) sent.getParameters().get("chat_id");

        assertThat(text)
            .isNotNull()
            .contains("Доступные команды");

        assertThat(chatIdSent).isEqualTo(chatId.toString());
    }

    @Test
    void testUnknownCommand() {

        // Arrange
        Long chatId = 789L;

        Update update = mock(Update.class);
        Message message = mock(Message.class);
        Chat chat = mock(Chat.class);

        when(update.message()).thenReturn(message);
        when(message.text()).thenReturn("/unknown");
        when(message.chat()).thenReturn(chat);
        when(chat.id()).thenReturn(chatId);

        // Act
        service.handleUpdate(update);

        // Assert
        ArgumentCaptor<SendMessage> captor = ArgumentCaptor.forClass(SendMessage.class);
        verify(telegramBot).execute(captor.capture());

        SendMessage sent = captor.getValue();
        String text = (String) sent.getParameters().get("text");
        String chatIdSent = (String) sent.getParameters().get("chat_id");

        assertThat(text)
            .isNotNull()
            .isEqualTo("Неизвестная команда. Воспользуйтесь /help.");

        assertThat(chatIdSent).isEqualTo(chatId.toString());
    }
}
