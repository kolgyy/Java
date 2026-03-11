package backend.academy.linktracker.bot.service;

import backend.academy.linktracker.bot.client.ScrapperClient;
import backend.academy.linktracker.bot.dto.request.AddLinkRequest;
import backend.academy.linktracker.bot.dto.request.RemoveLinkRequest;
import backend.academy.linktracker.bot.dto.response.LinkResponse;
import backend.academy.linktracker.bot.dto.response.ListLinksResponse;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScrapperService {

    private final ScrapperClient client;
    private final UserService userService;

    private void ensureChatRegistered(long chatId) {
        if (!userService.isRegistered(chatId)) {
            log.atInfo().addKeyValue("chatId", chatId).log("Chat not registered, registering automatically.");
            userService.register(chatId);
            registerChat(chatId);
        }
    }

    public void registerChat(long chatId) {
        client.registerChat(chatId);
        log.atInfo().addKeyValue("chatId", chatId).log("Sent request to register chat in Scrapper");
    }

    public void removeChat(long chatId) {
        client.removeChat(chatId);
        log.atInfo().addKeyValue("chatId", chatId).log("Sent request to remove chat from Scrapper");
    }

    public Optional<ListLinksResponse> getLinks(long chatId) {
        ensureChatRegistered(chatId);
        Optional<ListLinksResponse> responseOpt = client.getLinks(chatId);

        responseOpt.ifPresent(response -> log.atInfo()
                .addKeyValue("chatId", chatId)
                .addKeyValue("linksCount", response.size())
                .log("Successfully fetched links from Scrapper"));

        return responseOpt;
    }

    public Optional<LinkResponse> addLink(long chatId, AddLinkRequest request) {
        ensureChatRegistered(chatId);
        Optional<LinkResponse> responseOpt = client.addLink(chatId, request);

        responseOpt.ifPresent(response -> log.atInfo()
                .addKeyValue("chatId", chatId)
                .addKeyValue("url", request.link())
                .log("Successfully added link to Scrapper"));

        return responseOpt;
    }

    public Optional<LinkResponse> removeLink(long chatId, RemoveLinkRequest request) {
        ensureChatRegistered(chatId);
        Optional<LinkResponse> responseOpt = client.removeLink(chatId, request);

        responseOpt.ifPresent(response -> log.atInfo()
                .addKeyValue("chatId", chatId)
                .addKeyValue("url", request.link())
                .log("Successfully removed link from Scrapper"));

        return responseOpt;
    }
}
