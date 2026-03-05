package backend.academy.linktracker.bot.repository;

import backend.academy.linktracker.bot.model.Link;
import org.springframework.stereotype.Repository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class InMemoryLinkRepository implements LinkRepository {

    private final Map<Long, List<Link>> storage = new ConcurrentHashMap<>();

    @Override
    public List<Link> findAllByChatId(Long chatId) {
        return storage.getOrDefault(chatId, new ArrayList<>());
    }

    @Override
    public Optional<Link> findByChatIdAndUrl(Long chatId, String url) {
        return findAllByChatId(chatId).stream()
            .filter(link -> link.getUrl().equals(url))
            .findFirst();
    }

    @Override
    public Link save(Long chatId, Link link) {
        storage.computeIfAbsent(chatId, id -> new ArrayList<>()).add(link);
        return link;
    }

    @Override
    public void delete(Long chatId, String url) {
        findAllByChatId(chatId)
            .removeIf(link -> link.getUrl().equals(url));
    }
}
