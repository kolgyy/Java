package backend.academy.linktracker.scrapper.service;

import backend.academy.linktracker.scrapper.exception.LinkAlreadyExistsException;
import backend.academy.linktracker.scrapper.exception.LinkNotFoundException;
import backend.academy.linktracker.scrapper.model.Link;
import backend.academy.linktracker.scrapper.repository.LinkRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LinkService {

    private final LinkRepository repository;
    private final TgChatService tgChatService;

    public List<Link> getLinks(Long chatId) {

        tgChatService.assertChatRegistered(chatId);

        return repository.findAllByChatId(chatId);
    }

    public Link addLink(Long chatId, String url, List<String> tags, List<String> filters) {

        tgChatService.assertChatRegistered(chatId);

        if (repository.findByChatIdAndUrl(chatId, url).isPresent()) {
            throw new LinkAlreadyExistsException(url);
        }

        Link link = new Link(null, url, tags, filters);
        repository.save(chatId, link);

        return link;
    }

    public Link removeLink(Long chatId, String url) {

        tgChatService.assertChatRegistered(chatId);

        Link link = repository.findByChatIdAndUrl(chatId, url).orElseThrow(() -> new LinkNotFoundException(url));

        repository.delete(chatId, url);

        return link;
    }
}
