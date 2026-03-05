package backend.academy.linktracker.bot.repository;

import backend.academy.linktracker.bot.model.Link;
import java.util.List;
import java.util.Optional;

public interface LinkRepository {
    List<Link> findAllByChatId(Long chatId);

    Optional<Link> findByChatIdAndUrl(Long chatId, String url);

    Link save(Long chatId, Link link);

    void delete(Long chatId, String url);
}
