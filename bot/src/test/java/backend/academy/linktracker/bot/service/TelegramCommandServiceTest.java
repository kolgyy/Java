package backend.academy.linktracker.bot.service;

import static org.mockito.Mockito.*;

import backend.academy.linktracker.bot.command.Command;
import backend.academy.linktracker.bot.command.CommandRegistry;
import backend.academy.linktracker.bot.model.UserSession;
import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.model.Chat;
import com.pengrad.telegrambot.model.Message;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.request.SendMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class TelegramCommandServiceTest {

    private TelegramBot telegramBot;
    private CommandRegistry commandRegistry;
    private UserSessionService sessionService;

    private TelegramCommandService service;

    @BeforeEach
    void setup() {

        telegramBot = mock(TelegramBot.class);
        commandRegistry = mock(CommandRegistry.class);
        sessionService = mock(UserSessionService.class);

        service = new TelegramCommandService(commandRegistry, telegramBot, sessionService);
    }

    private Update createUpdate(Long chatId, String text) {

        Update update = mock(Update.class);
        Message message = mock(Message.class);
        Chat chat = mock(Chat.class);

        when(update.message()).thenReturn(message);
        when(message.text()).thenReturn(text);
        when(message.chat()).thenReturn(chat);
        when(chat.id()).thenReturn(chatId);

        return update;
    }

    @Test
    void shouldExecuteCommandWhenCommandExists() {

        Long chatId = 1L;

        Update update = createUpdate(chatId, "/start");

        Command command = mock(Command.class);
        UserSession session = new UserSession(chatId);

        when(sessionService.getSession(chatId)).thenReturn(session);
        when(commandRegistry.hasCommand("/start")).thenReturn(true);
        when(commandRegistry.getCommand("/start")).thenReturn(command);
        when(command.execute(eq(chatId), any())).thenReturn("response");

        service.handleUpdate(update);

        verify(sessionService).resetAll(chatId);
        verify(command).execute(eq(chatId), any());
        verify(telegramBot).execute(any(SendMessage.class));
    }

    @Test
    void shouldUseSessionCommandWhenNoNewCommand() {

        Long chatId = 2L;

        Update update = createUpdate(chatId, "some text");

        Command command = mock(Command.class);

        UserSession session = new UserSession(chatId);
        session.setCurrentCommand("/track");

        when(sessionService.getSession(chatId)).thenReturn(session);
        when(commandRegistry.hasCommand("some")).thenReturn(false);
        when(commandRegistry.getCommand("/track")).thenReturn(command);
        when(command.execute(eq(chatId), any())).thenReturn("track response");

        service.handleUpdate(update);

        verify(command).execute(eq(chatId), any());
        verify(telegramBot).execute(any(SendMessage.class));
    }

    @Test
    void shouldUseUnknownCommandWhenNoSessionCommand() {

        Long chatId = 3L;

        Update update = createUpdate(chatId, "/unknown");

        Command command = mock(Command.class);

        UserSession session = new UserSession(chatId);

        when(sessionService.getSession(chatId)).thenReturn(session);
        when(commandRegistry.hasCommand("/unknown")).thenReturn(false);
        when(commandRegistry.getCommand("/unknown")).thenReturn(command);
        when(command.execute(eq(chatId), any())).thenReturn("unknown");

        service.handleUpdate(update);

        verify(command).execute(eq(chatId), any());
        verify(telegramBot).execute(any(SendMessage.class));
    }

    @Test
    void shouldSendCorrectMessageToTelegram() {

        Long chatId = 4L;

        Update update = createUpdate(chatId, "/help");

        Command command = mock(Command.class);
        UserSession session = new UserSession(chatId);

        when(sessionService.getSession(chatId)).thenReturn(session);
        when(commandRegistry.hasCommand("/help")).thenReturn(true);
        when(commandRegistry.getCommand("/help")).thenReturn(command);
        when(command.execute(eq(chatId), any())).thenReturn("help text");

        service.handleUpdate(update);

        ArgumentCaptor<SendMessage> captor = ArgumentCaptor.forClass(SendMessage.class);

        verify(telegramBot).execute(captor.capture());

        SendMessage message = captor.getValue();

        String text = (String) message.getParameters().get("text");
        String chat = (String) message.getParameters().get("chat_id");

        assert text.equals("help text");
        assert chat.equals(chatId.toString());
    }

    @Test
    void shouldIgnoreUpdateWithoutMessage() {

        Update update = mock(Update.class);

        when(update.message()).thenReturn(null);

        service.handleUpdate(update);

        verifyNoInteractions(commandRegistry);
        verifyNoInteractions(telegramBot);
    }

    @Test
    void shouldIgnoreUpdateWithoutText() {

        Update update = mock(Update.class);
        Message message = mock(Message.class);

        when(update.message()).thenReturn(message);
        when(message.text()).thenReturn(null);

        service.handleUpdate(update);

        verifyNoInteractions(commandRegistry);
        verifyNoInteractions(telegramBot);
    }
}
