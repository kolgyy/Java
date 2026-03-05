package backend.academy.linktracker.bot.service;

import backend.academy.linktracker.bot.model.Link;
import backend.academy.linktracker.bot.repository.LinkRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LinkService {

    private final LinkRepository repository;

    public boolean hasLink(Long chatId, String url) {
        return repository.findByChatIdAndUrl(chatId, url).isPresent();
    }

    public Optional<Link> addLink(Long chatId, String url, List<String> tags) {
        if (hasLink(chatId, url)) {
            return Optional.empty();
        }
        Link link = new Link(url, tags);
        repository.save(chatId, link);
        return Optional.of(link);
    }

    public boolean removeLink(Long chatId, String url) {
        if (!hasLink(chatId, url)) {
            return false;
        }
        repository.delete(chatId, url);
        return true;
    }

    public List<Link> getLinks(Long chatId) {
        return repository.findAllByChatId(chatId);
    }

    public List<Link> getLinksByTag(Long chatId, String tag) {
        return repository.findAllByChatId(chatId).stream()
            .filter(link -> link.getTags() != null && link.getTags().contains(tag))
            .collect(Collectors.toList());
    }

    public Optional<Link> findLink(Long chatId, String url) {
        return repository.findByChatIdAndUrl(chatId, url);
    }
}
