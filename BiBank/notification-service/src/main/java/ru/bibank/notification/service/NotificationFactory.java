package ru.bibank.notification.service;

import org.springframework.stereotype.Component;
import ru.bibank.common.events.EventEnvelope;
import ru.bibank.common.events.EventTypes;
import ru.bibank.notification.entity.NotificationLevel;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Преобразует доменное событие в текст уведомления. Неизвестные события игнорируются.
 */
@Component
public class NotificationFactory {

    public Optional<NotificationTemplate> create(EventEnvelope event) {
        Map<String, Object> p = event.payload() == null ? Map.of() : event.payload();
        return Optional.ofNullable(switch (event.eventType()) {
            case EventTypes.USER_REGISTERED -> new NotificationTemplate(NotificationLevel.SUCCESS,
                    "Добро пожаловать в BiBank!",
                    "Регистрация завершена. Откройте первый счёт, чтобы начать пользоваться банком.");
            case EventTypes.ACCOUNT_CREATED -> new NotificationTemplate(NotificationLevel.SUCCESS,
                    "Открыт новый счёт",
                    "Счёт " + mask(p.get("number")) + " в валюте " + p.get("currency") + " готов к работе.");
            case EventTypes.ACCOUNT_STATUS_CHANGED -> new NotificationTemplate(NotificationLevel.WARNING,
                    "Изменён статус счёта",
                    "Счёт " + mask(p.get("number")) + ": " + statusName(p.get("newStatus")) + ".");
            case EventTypes.TRANSFER_COMPLETED -> new NotificationTemplate(NotificationLevel.SUCCESS,
                    "Перевод выполнен",
                    "Списано " + money(p.get("amount"), p.get("currency")) + " на счёт "
                            + mask(p.get("toAccountNumber")) + recipient(p.get("recipientName")) + ".");
            case EventTypes.TRANSFER_RECEIVED -> new NotificationTemplate(NotificationLevel.SUCCESS,
                    "Поступление средств",
                    "Зачислено " + money(p.get("creditedAmount"), p.get("creditedCurrency"))
                            + (blank(p.get("senderName")) ? "" : " от " + p.get("senderName")) + ".");
            case EventTypes.TRANSFER_FAILED -> new NotificationTemplate(NotificationLevel.ERROR,
                    "Перевод не выполнен",
                    "Перевод на " + money(p.get("amount"), p.get("currency")) + " отклонён: " + p.get("reason"));
            case EventTypes.TOP_UP_COMPLETED -> new NotificationTemplate(NotificationLevel.SUCCESS,
                    "Счёт пополнен",
                    "Зачислено " + money(p.get("amount"), p.get("currency")) + " на счёт "
                            + mask(p.get("toAccountNumber")) + ".");
            case EventTypes.LOAN_APPLICATION_SUBMITTED -> new NotificationTemplate(NotificationLevel.INFO,
                    "Заявка на кредит принята",
                    "Заявка на " + money(p.get("amount"), p.get("currency")) + " («" + p.get("productName")
                            + "») отправлена на рассмотрение.");
            case EventTypes.LOAN_APPLICATION_STATUS_CHANGED -> applicationStatus(p);
            case EventTypes.LOAN_PAYMENT_MADE -> new NotificationTemplate(NotificationLevel.SUCCESS,
                    "Платёж по кредиту",
                    "Внесено " + money(p.get("amount"), p.get("currency")) + ". Остаток долга: "
                            + money(p.get("remaining"), p.get("currency")) + ".");
            case EventTypes.LOAN_CLOSED -> new NotificationTemplate(NotificationLevel.SUCCESS,
                    "Кредит погашен", "Кредит «" + p.get("productName") + "» полностью погашен. Спасибо!");
            case EventTypes.DEPOSIT_OPENED -> new NotificationTemplate(NotificationLevel.SUCCESS,
                    "Вклад открыт",
                    "Вклад «" + p.get("productName") + "» на " + money(p.get("amount"), p.get("currency"))
                            + " под " + p.get("rate") + "% годовых.");
            case EventTypes.DEPOSIT_CLOSED -> new NotificationTemplate(NotificationLevel.INFO,
                    "Вклад закрыт",
                    "Вклад «" + p.get("productName") + "» закрыт. На счёт возвращено "
                            + money(p.get("payout"), p.get("currency")) + ", в том числе проценты "
                            + money(p.get("interest"), p.get("currency")) + ".");
            case EventTypes.USER_STATUS_CHANGED -> "ACTIVE".equals(p.get("status"))
                    ? new NotificationTemplate(NotificationLevel.INFO, "Учётная запись разблокирована",
                    "Доступ к BiBank восстановлен.")
                    : null;
            default -> null;
        });
    }

    private NotificationTemplate applicationStatus(Map<String, Object> p) {
        String status = String.valueOf(p.get("status"));
        return switch (status) {
            case "APPROVED" -> new NotificationTemplate(NotificationLevel.SUCCESS, "Кредит одобрен",
                    "Заявка одобрена, " + money(p.get("amount"), p.get("currency")) + " зачислено на ваш счёт.");
            case "REJECTED" -> new NotificationTemplate(NotificationLevel.WARNING, "Заявка отклонена",
                    "К сожалению, заявка на кредит отклонена"
                            + (blank(p.get("reason")) ? "." : ": " + p.get("reason")));
            case "CANCELLED" -> new NotificationTemplate(NotificationLevel.INFO, "Заявка отменена",
                    "Заявка на кредит отменена.");
            default -> new NotificationTemplate(NotificationLevel.INFO, "Статус заявки изменён",
                    "Новый статус заявки: " + status);
        };
    }

    static String money(Object amount, Object currency) {
        if (amount == null) {
            return "—";
        }
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.forLanguageTag("ru"));
        symbols.setGroupingSeparator(' ');
        DecimalFormat format = new DecimalFormat("#,##0.00", symbols);
        String symbol = switch (String.valueOf(currency)) {
            case "RUB" -> "₽";
            case "USD" -> "$";
            case "EUR" -> "€";
            default -> String.valueOf(currency);
        };
        return format.format(new BigDecimal(amount.toString())) + " " + symbol;
    }

    static String mask(Object number) {
        String s = String.valueOf(number);
        return s.length() > 4 ? "•• " + s.substring(s.length() - 4) : s;
    }

    private static String recipient(Object name) {
        return blank(name) ? "" : " (" + name + ")";
    }

    private static String statusName(Object status) {
        return switch (String.valueOf(status)) {
            case "ACTIVE" -> "активен";
            case "FROZEN" -> "заморожен";
            case "CLOSED" -> "закрыт";
            default -> String.valueOf(status);
        };
    }

    private static boolean blank(Object o) {
        return o == null || o.toString().isBlank();
    }
}
