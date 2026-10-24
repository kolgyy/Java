package ru.bibank.account.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import ru.bibank.account.dto.AccountResponse;
import ru.bibank.account.dto.AccountStatsResponse;
import ru.bibank.account.entity.AccountStatus;
import ru.bibank.account.service.AccountService;
import ru.bibank.common.web.PageResponse;

@RestController
@RequestMapping("/api/admin/accounts")
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
@Tag(name = "Администрирование счетов")
public class AdminAccountController {

    private final AccountService service;

    public AdminAccountController(AccountService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Все счета клиентов")
    public PageResponse<AccountResponse> list(@RequestParam(required = false) Long ownerId,
                                              @RequestParam(required = false) AccountStatus status,
                                              @RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "20") int size) {
        return service.search(ownerId, status, page, size);
    }

    @GetMapping("/stats")
    @Operation(summary = "Сводка по счетам и остаткам")
    public AccountStatsResponse stats() {
        return service.stats();
    }
}
