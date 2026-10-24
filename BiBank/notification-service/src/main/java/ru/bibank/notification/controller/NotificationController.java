package ru.bibank.notification.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;
import ru.bibank.common.security.CurrentUser;
import ru.bibank.common.web.PageResponse;
import ru.bibank.notification.dto.NotificationResponse;
import ru.bibank.notification.service.NotificationService;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/notifications")
@Tag(name = "Уведомления")
public class NotificationController {

    private final NotificationService service;

    public NotificationController(NotificationService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Уведомления текущего пользователя")
    public PageResponse<NotificationResponse> list(@RequestParam(defaultValue = "false") boolean unreadOnly,
                                                   @RequestParam(defaultValue = "0") int page,
                                                   @RequestParam(defaultValue = "20") int size) {
        return service.list(CurrentUser.id(), unreadOnly, page, size);
    }

    @GetMapping("/unread-count")
    @Operation(summary = "Количество непрочитанных")
    public Map<String, Long> unreadCount() {
        return Map.of("count", service.unreadCount(CurrentUser.id()));
    }

    @PostMapping("/{id}/read")
    @Operation(summary = "Отметить прочитанным")
    public NotificationResponse read(@PathVariable UUID id) {
        return service.markRead(CurrentUser.id(), id);
    }

    @PostMapping("/read-all")
    @Operation(summary = "Отметить все прочитанными")
    public Map<String, Integer> readAll() {
        return Map.of("updated", service.markAllRead(CurrentUser.id()));
    }
}
