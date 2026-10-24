package ru.bibank.auth.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.bibank.auth.dto.*;
import ru.bibank.auth.entity.Role;
import ru.bibank.auth.entity.User;
import ru.bibank.auth.entity.UserStatus;
import ru.bibank.auth.repository.UserRepository;
import ru.bibank.auth.security.JwtService;
import ru.bibank.common.events.EventEnvelope;
import ru.bibank.common.events.EventTypes;
import ru.bibank.common.events.Topics;
import ru.bibank.common.outbox.OutboxWriter;
import ru.bibank.common.web.ApiException;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    static final int MAX_FAILED_LOGINS = 5;
    static final Duration LOCK_WINDOW = Duration.ofMinutes(15);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final SessionStore sessionStore;
    private final OutboxWriter outboxWriter;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            SessionStore sessionStore,
            OutboxWriter outboxWriter
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.sessionStore = sessionStore;
        this.outboxWriter = outboxWriter;
    }

    @Transactional
    public UserResponse register(RegisterRequest request) {

        String email = request.email().trim().toLowerCase();
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw ApiException.conflict("USER_EXISTS", "Пользователь с таким email уже зарегистрирован");
        }

        User user = new User();
        user.setEmail(email);
        user.setFullName(request.fullName().trim());
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setRole(Role.ROLE_USER);
        user.setStatus(UserStatus.ACTIVE);

        userRepository.save(user);

        outboxWriter.write(Topics.USER_EVENTS, EventEnvelope.of(
                EventTypes.USER_REGISTERED, "auth-service", user.getId(), user.getId(),
                Map.of("email", user.getEmail(), "fullName", user.getFullName())));

        log.info("User registered: id={}", user.getId());
        return UserResponse.from(user);
    }

    @Transactional
    public LoginResponse login(LoginRequest request) {

        String email = request.email().trim().toLowerCase();

        if (sessionStore.failedLogins(email) >= MAX_FAILED_LOGINS) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_ATTEMPTS",
                    "Слишком много неудачных попыток входа. Попробуйте через 15 минут");
        }

        User user = userRepository.findByEmailIgnoreCase(email).orElse(null);

        if (user == null || !passwordEncoder.matches(request.password(), user.getPassword())) {
            sessionStore.registerFailedLogin(email, LOCK_WINDOW);
            throw ApiException.unauthorized("Неверный email или пароль");
        }
        ensureActive(user);

        sessionStore.resetFailedLogins(email);
        user.setLastLoginAt(Instant.now());

        return issueTokens(user);
    }

    @Transactional(readOnly = true)
    public LoginResponse refresh(RefreshTokenRequest request) {

        Long userId = sessionStore.consume(request.refreshToken())
                .orElseThrow(() -> ApiException.unauthorized("Сессия истекла или refresh token недействителен"));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.unauthorized("Пользователь не найден"));
        ensureActive(user);

        return issueTokens(user);
    }

    public void logout(String refreshToken) {
        sessionStore.revoke(refreshToken);
    }

    private LoginResponse issueTokens(User user) {
        String accessToken = jwtService.generateAccessToken(user);
        String refreshToken = jwtService.generateRefreshToken();
        sessionStore.save(refreshToken, user.getId());
        return new LoginResponse(accessToken, refreshToken, "Bearer",
                jwtService.accessTokenTtlSeconds(), UserResponse.from(user));
    }

    private void ensureActive(User user) {
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw ApiException.forbidden("Учётная запись заблокирована");
        }
    }
}
