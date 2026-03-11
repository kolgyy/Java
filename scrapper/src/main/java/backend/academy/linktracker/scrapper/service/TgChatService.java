package backend.academy.linktracker.scrapper.service;

import backend.academy.linktracker.scrapper.exception.ChatAlreadyRegisteredException;
import backend.academy.linktracker.scrapper.exception.ChatNotRegisteredException;
import backend.academy.linktracker.scrapper.model.TgChat;
import backend.academy.linktracker.scrapper.repository.TgChatRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class TgChatService {

    private final TgChatRepository repository;

    public void assertChatRegistered(Long chatId) {
        if (!repository.existsById(chatId)) {
            log.atWarn().addKeyValue("chatId", chatId).log("Chat not registered");

            throw new ChatNotRegisteredException(chatId);
        }
    }

    public TgChat registerChat(Long chatId) {

        if (repository.existsById(chatId)) {
            log.atWarn().addKeyValue("chatId", chatId).log("Chat already exists");

            throw new ChatAlreadyRegisteredException(chatId);
        }

        TgChat chat = new TgChat(chatId);
        repository.save(chat);

        log.atInfo().addKeyValue("chatId", chatId).log("Registered new chat");

        return chat;
    }

    public void removeChat(Long chatId) {

        if (!repository.existsById(chatId)) {
            log.atWarn().addKeyValue("chatId", chatId).log("Chat not found for removal");

            throw new ChatNotRegisteredException(chatId);
        }

        repository.delete(chatId);

        log.atInfo().addKeyValue("chatId", chatId).log("Removed chat");
    }
}
