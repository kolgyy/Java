package ru.bibank.common.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import ru.bibank.common.events.EventEnvelope;

import java.sql.Timestamp;
import java.time.Instant;

/**
 * Transactional Outbox: событие сохраняется в той же транзакции, что и бизнес-изменение.
 * Публикацию в Kafka выполняет {@link OutboxPublisher}.
 */
@Component
@ConditionalOnProperty(name = "bibank.outbox.enabled", havingValue = "true")
public class OutboxWriter {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public OutboxWriter(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void write(String topic, EventEnvelope event) {
        try {
            jdbcTemplate.update("""
                            INSERT INTO outbox_events (id, topic, event_key, event_type, payload, created_at)
                            VALUES (?, ?, ?, ?, ?, ?)
                            """,
                    event.eventId(),
                    topic,
                    event.userId() == null ? null : event.userId().toString(),
                    event.eventType(),
                    objectMapper.writeValueAsString(event),
                    Timestamp.from(Instant.now()));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialize event " + event.eventType(), e);
        }
    }
}
