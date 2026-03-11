package backend.academy.linktracker.scrapper.exception;

import lombok.Getter;

@Getter
public class ChatAlreadyRegisteredException extends RuntimeException {

    private final Long chatId;

    public ChatAlreadyRegisteredException(Long chatId) {
        super("Chat already registered: " + chatId);
        this.chatId = chatId;
    }
}
