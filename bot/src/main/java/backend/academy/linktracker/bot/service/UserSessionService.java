package backend.academy.linktracker.bot.service;

import backend.academy.linktracker.bot.model.UserSession;
import backend.academy.linktracker.bot.repository.UserSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserSessionService {

    private final UserSessionRepository repository;

    public UserSession getSession(Long chatId) {
        return repository.findByChatId(chatId).orElseGet(() -> repository.save(new UserSession(chatId)));
    }

    public void resetState(Long chatId) {
        UserSession session = getSession(chatId);
        session.resetState();
        repository.save(session);
    }

    public void resetAll(Long chatId) {
        UserSession session = getSession(chatId);
        session.resetAll();
        repository.save(session);
    }

    public UserSession save(UserSession session) {
        return repository.save(session);
    }
}
