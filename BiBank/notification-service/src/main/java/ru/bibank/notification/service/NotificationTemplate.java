package ru.bibank.notification.service;

import ru.bibank.notification.entity.NotificationLevel;

public record NotificationTemplate(NotificationLevel level, String title, String message) {
}
