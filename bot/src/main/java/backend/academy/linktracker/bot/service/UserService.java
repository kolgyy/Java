package backend.academy.linktracker.bot.service;

import backend.academy.linktracker.bot.model.User;
import backend.academy.linktracker.bot.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    public boolean isRegistered(Long chatId) {
        return userRepository.existsByChatId(chatId);
    }

    public void register(Long chatId) {
        if (!userRepository.existsByChatId(chatId)) {
            userRepository.save(new User(chatId, true));

            log.atInfo().addKeyValue("chatId", chatId).log("New user registered");
        }
    }
}
