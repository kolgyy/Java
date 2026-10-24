package ru.bibank.notification.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.bibank.notification.entity.Notification;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    Page<Notification> findByUserId(Long userId, Pageable pageable);

    Page<Notification> findByUserIdAndReadFalse(Long userId, Pageable pageable);

    long countByUserIdAndReadFalse(Long userId);

    Optional<Notification> findByIdAndUserId(UUID id, Long userId);

    /**
     * Вставка с игнорированием дубликата: повторная доставка того же события не создаёт второе уведомление.
     *
     * @return 1 — уведомление создано, 0 — событие уже было обработано
     */
    @Modifying
    @Query(value = """
            INSERT INTO notifications (id, user_id, event_id, event_type, level, title, message, is_read, created_at)
            VALUES (:id, :userId, :eventId, :eventType, :level, :title, :message, false, :createdAt)
            ON CONFLICT (event_id, user_id) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(@Param("id") UUID id, @Param("userId") Long userId, @Param("eventId") UUID eventId,
                       @Param("eventType") String eventType, @Param("level") String level,
                       @Param("title") String title, @Param("message") String message,
                       @Param("createdAt") Instant createdAt);

    @Modifying
    @Query("UPDATE Notification n SET n.read = true, n.readAt = :now WHERE n.userId = :userId AND n.read = false")
    int markAllRead(@Param("userId") Long userId, @Param("now") Instant now);
}
