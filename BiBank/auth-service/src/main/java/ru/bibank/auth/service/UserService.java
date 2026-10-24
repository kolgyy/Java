package ru.bibank.auth.service;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.bibank.auth.dto.UpdateProfileRequest;
import ru.bibank.auth.dto.UserResponse;
import ru.bibank.auth.dto.UserStatsResponse;
import ru.bibank.auth.entity.Role;
import ru.bibank.auth.entity.User;
import ru.bibank.auth.entity.UserStatus;
import ru.bibank.auth.repository.UserRepository;
import ru.bibank.common.events.EventEnvelope;
import ru.bibank.common.events.EventTypes;
import ru.bibank.common.events.Topics;
import ru.bibank.common.outbox.OutboxWriter;
import ru.bibank.common.web.ApiException;
import ru.bibank.common.web.PageResponse;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final SessionStore sessionStore;
    private final OutboxWriter outboxWriter;

    public UserService(UserRepository userRepository, SessionStore sessionStore, OutboxWriter outboxWriter) {
        this.userRepository = userRepository;
        this.sessionStore = sessionStore;
        this.outboxWriter = outboxWriter;
    }

    @Transactional(readOnly = true)
    public UserResponse get(Long id) {
        return UserResponse.from(find(id));
    }

    @Transactional
    public UserResponse updateProfile(Long id, UpdateProfileRequest request) {
        User user = find(id);
        user.setFullName(request.fullName().trim());
        return UserResponse.from(user);
    }

    @Transactional(readOnly = true)
    public PageResponse<UserResponse> search(String search, UserStatus status, int page, int size) {
        String term = search == null || search.isBlank() ? null : search.trim();
        var pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        return PageResponse.of(userRepository.search(term, status, pageable), UserResponse::from);
    }

    @Transactional
    public UserResponse changeStatus(Long adminId, Long userId, UserStatus status) {
        if (adminId.equals(userId)) {
            throw ApiException.badRequest("SELF_STATUS_CHANGE", "Нельзя изменить статус собственной учётной записи");
        }
        User user = find(userId);
        if (user.getStatus() == status) {
            return UserResponse.from(user);
        }
        user.setStatus(status);

        boolean blocked = status == UserStatus.BLOCKED;
        sessionStore.setBlocked(userId, blocked);
        if (blocked) {
            sessionStore.revokeAll(userId);
        }

        outboxWriter.write(Topics.USER_EVENTS, EventEnvelope.of(
                EventTypes.USER_STATUS_CHANGED, "auth-service", userId, userId,
                Map.of("status", status.name())));
        return UserResponse.from(user);
    }

    @Transactional(readOnly = true)
    public UserStatsResponse stats() {
        return new UserStatsResponse(
                userRepository.count(),
                userRepository.countByStatus(UserStatus.ACTIVE),
                userRepository.countByStatus(UserStatus.BLOCKED),
                userRepository.countByRole(Role.ROLE_ADMIN),
                userRepository.countByCreatedAtAfter(Instant.now().minus(1, ChronoUnit.DAYS)));
    }

    private User find(Long id) {
        return userRepository.findById(id).orElseThrow(() -> ApiException.notFound("Пользователь не найден"));
    }
}
