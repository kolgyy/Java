package backend.academy.linktracker.bot.controller;

import backend.academy.linktracker.bot.dto.LinkUpdate;
import backend.academy.linktracker.bot.service.UpdateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/updates")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.communication.server", havingValue = "http", matchIfMissing = true)
@Slf4j
public class UpdateController {

    private final UpdateService updateService;

    @PostMapping
    public ResponseEntity<Void> update(@RequestBody LinkUpdate update) {

        log.atInfo()
                .addKeyValue("url", update.url())
                .addKeyValue("chatCount", update.tgChatIds().size())
                .log("Received link update");

        updateService.processUpdate(update);

        return ResponseEntity.ok().build();
    }
}
