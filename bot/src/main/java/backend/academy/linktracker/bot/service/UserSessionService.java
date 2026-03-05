package backend.academy.linktracker.bot.service;

import backend.academy.linktracker.bot.model.UserSession;
import backend.academy.linktracker.bot.model.UserState;
import backend.academy.linktracker.bot.repository.UserSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserSessionService {

    private final UserSessionRepository repository;

    public UserSession getSession(Long chatId) {
        return repository.findByChatId(chatId)
            .orElseGet(() -> repository.save(new UserSession(chatId)));
    }

    public void startTracking(Long chatId) {
        UserSession session = getSession(chatId);
        session.setCurrentLink(null);
        session.setCurrentTags(null);
        session.setState(UserState.AWAITING_LINK);
        repository.save(session);
    }

    public void setLink(Long chatId, String link) {
        UserSession session = getSession(chatId);
        session.setCurrentLink(link != null ? link.trim() : null);
        session.setState(UserState.AWAITING_TAGS);
        repository.save(session);
    }

    public UserSession setTags(Long chatId, String tags) {
        UserSession session = getSession(chatId);
        session.setCurrentTags(tags != null ? tags.trim() : null);
        session.setState(UserState.IDLE);
        return repository.save(session);
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
