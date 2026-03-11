package backend.academy.linktracker.scrapper.repository;

import backend.academy.linktracker.scrapper.model.TgChat;
import java.util.List;
import java.util.Optional;

public interface TgChatRepository {

    Optional<TgChat> findById(Long id);

    boolean existsById(Long id);

    TgChat save(TgChat chat);

    void delete(Long id);

    List<TgChat> findAll();
}
