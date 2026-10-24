package ru.bibank.common.events;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Формат всех доменных событий BiBank в Kafka.
 *
 * @param eventId     уникальный идентификатор события (используется для дедупликации у потребителей)
 * @param eventType   тип события, см. {@link EventTypes}
 * @param source      сервис-источник
 * @param userId      пользователь, которого касается событие (ключ сообщения Kafka)
 * @param aggregateId идентификатор сущности (счёт, перевод, заявка...)
 * @param occurredAt  момент фиксации бизнес-изменения
 * @param payload     данные события
 */
public record EventEnvelope(
        UUID eventId,
        String eventType,
        String source,
        Long userId,
        String aggregateId,
        Instant occurredAt,
        Map<String, Object> payload
) {
    public static EventEnvelope of(String eventType, String source, Long userId, Object aggregateId,
                                   Map<String, Object> payload) {
        return new EventEnvelope(UUID.randomUUID(), eventType, source, userId,
                String.valueOf(aggregateId), Instant.now(), payload);
    }
}
