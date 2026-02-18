package backend.academy.linktracker.bot.repository;

import backend.academy.linktracker.bot.model.User;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Repository;

@Repository
public class InMemoryUserRepository implements UserRepository {

    // ConcurrentHashMap is required for TelegramBot API
    private final Map<Long, User> storage = new ConcurrentHashMap<>();

    @Override
    public Optional<User> findByChadId(Long chatId) {
        return Optional.ofNullable(storage.get(chatId));
    }

    @Override
    public User save(User user) {
        storage.put(user.getChatId(), user);
        return user;
    }

    @Override
    public boolean existsByChatId(Long chatId) {
        return storage.containsKey(chatId);
    }
}
