package backend.academy.linktracker.scrapper.repository;

import backend.academy.linktracker.scrapper.model.TgChat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Repository;

@Repository
public class InMemoryTgChatRepository implements TgChatRepository {

    private final Map<Long, TgChat> storage = new ConcurrentHashMap<>();

    @Override
    public Optional<TgChat> findById(Long id) {
        return Optional.ofNullable(storage.get(id));
    }

    @Override
    public boolean existsById(Long id) {
        return storage.containsKey(id);
    }

    @Override
    public TgChat save(TgChat chat) {
        storage.put(chat.getId(), chat);
        return chat;
    }

    @Override
    public void delete(Long id) {
        storage.remove(id);
    }

    @Override
    public List<TgChat> findAll() {
        return new ArrayList<>(storage.values());
    }
}
