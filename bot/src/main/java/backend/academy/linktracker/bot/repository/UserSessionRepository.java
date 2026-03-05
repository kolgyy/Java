package backend.academy.linktracker.bot.repository;

import backend.academy.linktracker.bot.model.UserSession;
import java.util.Optional;

public interface UserSessionRepository {

    Optional<UserSession> findByChatId(Long charId);

    UserSession save(UserSession session);

    boolean existsByChatId(Long chatId);
}
