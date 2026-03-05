package backend.academy.linktracker.bot.repository;

import backend.academy.linktracker.bot.model.UserSession;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Repository;

@Repository
public class InMemoryUserSessionRepository implements UserSessionRepository {

    private final Map<Long, UserSession> storage = new ConcurrentHashMap<>();

    @Override
    public Optional<UserSession> findByChatId(Long chatId) {
        return Optional.ofNullable(storage.get(chatId));
    }

    @Override
    public UserSession save(UserSession session) {
        storage.put(session.getChatId(), session);
        return session;
    }

    @Override
    public boolean existsByChatId(Long chatId) {
        return storage.containsKey(chatId);
    }
}
