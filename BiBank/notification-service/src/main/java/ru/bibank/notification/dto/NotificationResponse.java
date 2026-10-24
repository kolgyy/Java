package ru.bibank.notification.dto;

import ru.bibank.notification.entity.Notification;

import java.time.Instant;
import java.util.UUID;

public record NotificationResponse(
        UUID id,
        String eventType,
        String level,
        String title,
        String message,
        boolean read,
        Instant createdAt
) {
    public static NotificationResponse from(Notification n) {
        return new NotificationResponse(n.getId(), n.getEventType(), n.getLevel().name(), n.getTitle(),
                n.getMessage(), n.isRead(), n.getCreatedAt());
    }
}
