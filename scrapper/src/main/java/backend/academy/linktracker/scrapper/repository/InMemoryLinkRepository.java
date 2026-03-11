package backend.academy.linktracker.scrapper.repository;

import backend.academy.linktracker.scrapper.model.GithubTrackedLink;
import backend.academy.linktracker.scrapper.model.Link;
import backend.academy.linktracker.scrapper.model.StackoverflowTrackedLink;
import backend.academy.linktracker.scrapper.model.TrackedLink;
import backend.academy.linktracker.scrapper.model.UnknownTrackedLink;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class InMemoryLinkRepository implements LinkRepository {

    private final Map<Long, List<TrackedLink>> storage = new ConcurrentHashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(1);

    @Override
    public List<Link> findAll() {
        return storage.values().stream()
                .flatMap(List::stream)
                .map(TrackedLink::getLink)
                .toList();
    }

    @Override
    public List<Link> findAllByChatId(Long chatId) {
        return storage.getOrDefault(chatId, Collections.emptyList()).stream()
                .map(TrackedLink::getLink)
                .toList();
    }

    @Override
    public Optional<Link> findByChatIdAndUrl(Long chatId, String url) {
        return findAllByChatId(chatId).stream()
                .filter(l -> l.getUrl().equals(url))
                .findFirst();
    }

    @Override
    public Link save(Long chatId, Link link) {
        if (link.getId() == null) {
            link.setId(idGenerator.getAndIncrement());
        }

        TrackedLink tracked = createTrackedLink(link);

        storage.computeIfAbsent(chatId, id -> new ArrayList<>()).add(tracked);
        return link;
    }

    @Override
    public void delete(Long chatId, String url) {
        storage.getOrDefault(chatId, Collections.emptyList())
                .removeIf(tracked -> tracked.getLink().getUrl().equals(url));
    }

    @Override
    public Optional<TrackedLink> findTrackedByChatIdAndUrl(Long chatId, String url) {
        return storage.getOrDefault(chatId, Collections.emptyList()).stream()
                .filter(t -> t.getLink().getUrl().equals(url))
                .findFirst();
    }

    @Override
    public List<TrackedLink> findAllTrackedByChatId(Long chatId) {
        return new ArrayList<>(storage.getOrDefault(chatId, Collections.emptyList()));
    }

    private TrackedLink createTrackedLink(Link link) {
        String url = link.getUrl();
        if (url.contains("github.com")) {
            String[] parts = url.split("/");
            String owner = parts.length > 4 ? parts[3] : "";
            String repo = parts.length > 4 ? parts[4] : "";
            return new GithubTrackedLink(link, owner, repo);
        } else if (url.contains("stackoverflow.com")) {
            String[] parts = url.split("/");
            String questionId = parts.length > 4 ? parts[4] : "";
            return new StackoverflowTrackedLink(link, questionId);
        } else {
            return new UnknownTrackedLink(link);
        }
    }
}
