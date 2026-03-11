package backend.academy.linktracker.scrapper.controller;

import backend.academy.linktracker.scrapper.model.TgChat;
import backend.academy.linktracker.scrapper.service.TgChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/tg-chat")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.communication.server", havingValue = "http")
public class TgChatController {

    private final TgChatService service;

    @PostMapping("/{id}")
    public ResponseEntity<TgChat> registerChat(@PathVariable("id") Long chatId) {

        service.registerChat(chatId);

        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> removeChat(@PathVariable("id") Long chatId) {

        service.removeChat(chatId);

        return ResponseEntity.ok().build();
    }
}
