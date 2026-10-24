package ru.bibank.notification.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.bibank.common.events.EventEnvelope;
import ru.bibank.common.web.ApiException;
import ru.bibank.common.web.PageResponse;
import ru.bibank.notification.dto.NotificationResponse;
import ru.bibank.notification.entity.Notification;
import ru.bibank.notification.repository.NotificationRepository;

import java.time.Instant;
import java.util.UUID;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository repository;
    private final NotificationFactory factory;

    public NotificationService(NotificationRepository repository, NotificationFactory factory) {
        this.repository = repository;
        this.factory = factory;
    }

    /**
     * Идемпотентная обработка события.
     *
     * @return true, если создано новое уведомление
     */
    @Transactional
    public boolean handle(EventEnvelope event) {
        if (event.eventId() == null || event.eventType() == null) {
            throw new IllegalArgumentException("Event without id or type");
        }
        if (event.userId() == null || event.userId() <= 0) {
            return false;
        }
        return factory.create(event).map(t -> {
            int inserted = repository.insertIfAbsent(UUID.randomUUID(), event.userId(), event.eventId(),
                    event.eventType(), t.level().name(), t.title(), t.message(),
                    event.occurredAt() == null ? Instant.now() : event.occurredAt());
            if (inserted == 0) {
                log.info("Duplicate event {} ({}) skipped", event.eventId(), event.eventType());
            }
            return inserted == 1;
        }).orElse(false);
    }

    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> list(Long userId, boolean unreadOnly, int page, int size) {
        var pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        var result = unreadOnly
                ? repository.findByUserIdAndReadFalse(userId, pageable)
                : repository.findByUserId(userId, pageable);
        return PageResponse.of(result, NotificationResponse::from);
    }

    @Transactional(readOnly = true)
    public long unreadCount(Long userId) {
        return repository.countByUserIdAndReadFalse(userId);
    }

    @Transactional
    public NotificationResponse markRead(Long userId, UUID id) {
        Notification n = repository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> ApiException.notFound("Уведомление не найдено"));
        if (!n.isRead()) {
            n.setRead(true);
            n.setReadAt(Instant.now());
        }
        return NotificationResponse.from(n);
    }

    @Transactional
    public int markAllRead(Long userId) {
        return repository.markAllRead(userId, Instant.now());
    }
}
