package ru.bibank.notification;

import org.junit.jupiter.api.Test;
import ru.bibank.common.events.EventEnvelope;
import ru.bibank.common.events.EventTypes;
import ru.bibank.notification.entity.NotificationLevel;
import ru.bibank.notification.service.NotificationFactory;
import ru.bibank.notification.service.NotificationTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationFactoryTest {

    private final NotificationFactory factory = new NotificationFactory();

    @Test
    void transferReceivedMentionsAmountAndSender() {
        NotificationTemplate t = factory.create(event(EventTypes.TRANSFER_RECEIVED, Map.of(
                "creditedAmount", "1250.5", "creditedCurrency", "RUB", "senderName", "Алиса И."))).orElseThrow();

        assertThat(t.level()).isEqualTo(NotificationLevel.SUCCESS);
        assertThat(t.title()).isEqualTo("Поступление средств");
        assertThat(t.message()).isEqualTo("Зачислено 1 250,50 ₽ от Алиса И..");
    }

    @Test
    void accountNumberIsMasked() {
        NotificationTemplate t = factory.create(event(EventTypes.ACCOUNT_CREATED, Map.of(
                "number", "40817810123456789012", "currency", "RUB"))).orElseThrow();

        assertThat(t.message()).contains("•• 9012").doesNotContain("40817810123456789012");
    }

    @Test
    void failedTransferIsAnError() {
        NotificationTemplate t = factory.create(event(EventTypes.TRANSFER_FAILED, Map.of(
                "amount", "10", "currency", "USD", "reason", "Недостаточно средств"))).orElseThrow();

        assertThat(t.level()).isEqualTo(NotificationLevel.ERROR);
        assertThat(t.message()).contains("10,00 $").endsWith("Недостаточно средств");
    }

    @Test
    void unknownEventsAndBlockingDoNotNotify() {
        assertThat(factory.create(event("SomethingElse", Map.of()))).isEmpty();
        assertThat(factory.create(event(EventTypes.USER_STATUS_CHANGED, Map.of("status", "BLOCKED")))).isEmpty();
    }

    private static EventEnvelope event(String type, Map<String, Object> payload) {
        return EventEnvelope.of(type, "test", 1L, "agg", payload);
    }
}
