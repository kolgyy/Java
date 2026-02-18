package backend.academy.linktracker.bot.service;

import backend.academy.linktracker.bot.model.User;
import backend.academy.linktracker.bot.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    public boolean isRegistered(Long chatId) {
        return userRepository.existsByChatId(chatId);
    }

    public void register(Long charId) {
        if (!userRepository.existsByChatId(charId)) {
            userRepository.save(new User(charId, true));
        }
    }
}
