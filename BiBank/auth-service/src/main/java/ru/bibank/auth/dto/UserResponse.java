package ru.bibank.auth.dto;

import ru.bibank.auth.entity.User;

import java.time.Instant;

public record UserResponse(
        Long id,
        String email,
        String fullName,
        String role,
        String status,
        Instant createdAt,
        Instant lastLoginAt
) {
    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getFullName(), user.getRole().name(),
                user.getStatus().name(), user.getCreatedAt(), user.getLastLoginAt());
    }
}
