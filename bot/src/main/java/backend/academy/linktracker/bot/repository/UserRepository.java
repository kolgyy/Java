package backend.academy.linktracker.bot.repository;

import backend.academy.linktracker.bot.model.User;
import java.util.Optional;

public interface UserRepository {

    Optional<User> findByChatId(Long chatId);

    User save(User user);

    boolean existsByChatId(Long chatId);
}
