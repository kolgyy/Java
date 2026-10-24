package ru.bibank.common.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Периодически читает неопубликованные события из outbox и отправляет их в Kafka.
 * FOR UPDATE SKIP LOCKED позволяет запускать несколько экземпляров сервиса.
 * Гарантия доставки — at-least-once, поэтому потребители дедуплицируют события по eventId.
 */
@Component
@ConditionalOnProperty(name = "bibank.outbox.enabled", havingValue = "true")
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);
    private static final int BATCH_SIZE = 100;

    private final JdbcTemplate jdbcTemplate;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final TransactionTemplate transactionTemplate;

    public OutboxPublisher(JdbcTemplate jdbcTemplate,
                           KafkaTemplate<String, String> kafkaTemplate,
                           TransactionTemplate transactionTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.kafkaTemplate = kafkaTemplate;
        this.transactionTemplate = transactionTemplate;
    }

    private record Row(UUID id, String topic, String key, String payload) {
    }

    @Scheduled(fixedDelayString = "${bibank.outbox.poll-interval-ms:1000}")
    public void publish() {
        transactionTemplate.executeWithoutResult(status -> {
            List<Row> rows = jdbcTemplate.query("""
                            SELECT id, topic, event_key, payload FROM outbox_events
                            WHERE published_at IS NULL
                            ORDER BY created_at
                            LIMIT ?
                            FOR UPDATE SKIP LOCKED
                            """,
                    (rs, i) -> new Row(rs.getObject("id", UUID.class), rs.getString("topic"),
                            rs.getString("event_key"), rs.getString("payload")),
                    BATCH_SIZE);

            for (Row row : rows) {
                try {
                    kafkaTemplate.send(row.topic(), row.key(), row.payload()).get(10, TimeUnit.SECONDS);
                    jdbcTemplate.update("UPDATE outbox_events SET published_at = now(), attempts = attempts + 1 WHERE id = ?",
                            row.id());
                } catch (Exception e) {
                    if (e instanceof InterruptedException) {
                        Thread.currentThread().interrupt();
                    }
                    log.warn("Outbox publish failed for event {}: {}", row.id(), e.getMessage());
                    jdbcTemplate.update("UPDATE outbox_events SET attempts = attempts + 1, last_error = ? WHERE id = ?",
                            String.valueOf(e.getMessage()), row.id());
                    // Сохраняем порядок: следующие события будут отправлены в следующем цикле.
                    break;
                }
            }
        });
    }
}
