package ru.bibank.auth.dto;

public record UserStatsResponse(
        long totalUsers,
        long activeUsers,
        long blockedUsers,
        long admins,
        long registeredLast24h
) {
}
