package ru.bibank.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.kafka.core.KafkaTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import ru.bibank.common.events.EventEnvelope;
import ru.bibank.common.events.EventTypes;
import ru.bibank.common.events.Topics;
import ru.bibank.notification.repository.NotificationRepository;
import ru.bibank.notification.service.NotificationService;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Событие проходит путь Kafka → listener → PostgreSQL, а повторная доставка
 * того же события (at-least-once) не создаёт дубликат уведомления.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
        "bibank.security.jwt-secret=test-secret-test-secret-test-secret-123456",
        "bibank.security.internal-token=test-internal-token"
})
class NotificationKafkaIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    @ServiceConnection
    static KafkaContainer kafka = new KafkaContainer("apache/kafka:3.9.1");

    @Autowired
    KafkaTemplate<String, String> kafkaTemplate;
    @Autowired
    ObjectMapper objectMapper;
    @Autowired
    NotificationRepository repository;
    @Autowired
    NotificationService service;

    @Test
    void eventFromKafkaBecomesSingleNotificationEvenIfDeliveredTwice() throws Exception {
        long userId = 4242L;
        EventEnvelope event = EventEnvelope.of(EventTypes.TOP_UP_COMPLETED, "payment-service", userId, "op-1",
                Map.of("amount", "500.00", "currency", "RUB", "toAccountNumber", "40817810000000001234"));
        String json = objectMapper.writeValueAsString(event);

        kafkaTemplate.send(Topics.PAYMENT_EVENTS, String.valueOf(userId), json).get();
        kafkaTemplate.send(Topics.PAYMENT_EVENTS, String.valueOf(userId), json).get();

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(service.unreadCount(userId)).isEqualTo(1));
        Thread.sleep(1500);
        assertThat(service.unreadCount(userId)).isEqualTo(1);
        assertThat(service.list(userId, false, 0, 10).content().getFirst().message())
                .isEqualTo("Зачислено 500,00 ₽ на счёт •• 1234.");
    }

    @Test
    void handleIsIdempotentForTheSameEventId() {
        EventEnvelope event = new EventEnvelope(java.util.UUID.randomUUID(), EventTypes.USER_REGISTERED, "auth-service",
                777L, "777", Instant.now(), Map.of());
        assertThat(service.handle(event)).isTrue();
        assertThat(service.handle(event)).isFalse();
        assertThat(service.unreadCount(777L)).isEqualTo(1);
    }
}
