package backend.academy.linktracker.scrapper.repository;

import backend.academy.linktracker.scrapper.model.Link;
import backend.academy.linktracker.scrapper.model.TrackedLink;
import java.util.List;
import java.util.Optional;

public interface LinkRepository {

    List<Link> findAll();

    List<Link> findAllByChatId(Long chatId);

    Optional<Link> findByChatIdAndUrl(Long chatId, String url);

    Link save(Long chatId, Link link);

    void delete(Long chatId, String url);

    Optional<TrackedLink> findTrackedByChatIdAndUrl(Long chatId, String url);

    List<TrackedLink> findAllTrackedByChatId(Long chatId);
}
