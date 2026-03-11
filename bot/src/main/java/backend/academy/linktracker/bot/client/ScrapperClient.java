package backend.academy.linktracker.bot.client;

import backend.academy.linktracker.bot.dto.request.AddLinkRequest;
import backend.academy.linktracker.bot.dto.request.RemoveLinkRequest;
import backend.academy.linktracker.bot.dto.response.LinkResponse;
import backend.academy.linktracker.bot.dto.response.ListLinksResponse;
import java.util.Optional;

public interface ScrapperClient {
    void registerChat(long chatId);

    void removeChat(long chatId);

    Optional<ListLinksResponse> getLinks(long chatId);

    Optional<LinkResponse> addLink(long chatId, AddLinkRequest request);

    Optional<LinkResponse> removeLink(long chatId, RemoveLinkRequest request);
}
