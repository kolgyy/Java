package ru.bibank.notification.listener;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import ru.bibank.common.events.EventEnvelope;
import ru.bibank.common.events.Topics;
import ru.bibank.notification.service.NotificationService;

/**
 * Потребитель доменных событий. Группа notification-service; смещение фиксируется
 * только после успешной обработки (at-least-once), дубликаты отсекаются в БД.
 */
@Component
public class EventListener {

    private static final Logger log = LoggerFactory.getLogger(EventListener.class);

    private final ObjectMapper objectMapper;
    private final NotificationService notificationService;

    public EventListener(ObjectMapper objectMapper, NotificationService notificationService) {
        this.objectMapper = objectMapper;
        this.notificationService = notificationService;
    }

    @KafkaListener(
            topics = {Topics.USER_EVENTS, Topics.ACCOUNT_EVENTS, Topics.PAYMENT_EVENTS, Topics.LOAN_EVENTS},
            groupId = "${spring.kafka.consumer.group-id}")
    public void onEvent(ConsumerRecord<String, String> record) throws JsonProcessingException {
        EventEnvelope event = objectMapper.readValue(record.value(), EventEnvelope.class);
        boolean created = notificationService.handle(event);
        log.debug("Event {} {} from {} → notification created: {}",
                event.eventType(), event.eventId(), record.topic(), created);
    }
}
