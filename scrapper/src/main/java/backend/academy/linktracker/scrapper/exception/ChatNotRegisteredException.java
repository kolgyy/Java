package backend.academy.linktracker.scrapper.exception;

import lombok.Getter;

@Getter
public class ChatNotRegisteredException extends RuntimeException {

    private final Long chatId;

    public ChatNotRegisteredException(Long chatId) {
        super("Chat not registered: " + chatId);
        this.chatId = chatId;
    }
}
