package ru.bibank.auth.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import ru.bibank.auth.dto.ChangeStatusRequest;
import ru.bibank.auth.dto.UserResponse;
import ru.bibank.auth.dto.UserStatsResponse;
import ru.bibank.auth.entity.UserStatus;
import ru.bibank.auth.service.UserService;
import ru.bibank.common.security.CurrentUser;
import ru.bibank.common.web.PageResponse;

@RestController
@RequestMapping("/api/admin/users")
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
@Tag(name = "Администрирование пользователей")
public class AdminUserController {

    private final UserService userService;

    public AdminUserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    @Operation(summary = "Список пользователей с поиском и фильтром по статусу")
    public PageResponse<UserResponse> list(@RequestParam(required = false) String search,
                                           @RequestParam(required = false) UserStatus status,
                                           @RequestParam(defaultValue = "0") int page,
                                           @RequestParam(defaultValue = "20") int size) {
        return userService.search(search, status, page, size);
    }

    @GetMapping("/stats")
    @Operation(summary = "Статистика по пользователям")
    public UserStatsResponse stats() {
        return userService.stats();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Информация о пользователе")
    public UserResponse get(@PathVariable Long id) {
        return userService.get(id);
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Блокировка / разблокировка пользователя")
    public UserResponse changeStatus(@PathVariable Long id, @Valid @RequestBody ChangeStatusRequest request) {
        return userService.changeStatus(CurrentUser.id(), id, request.status());
    }
}
