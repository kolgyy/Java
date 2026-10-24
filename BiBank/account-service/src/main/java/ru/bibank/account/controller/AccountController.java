package ru.bibank.account.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import ru.bibank.account.dto.*;
import ru.bibank.account.service.AccountService;
import ru.bibank.common.security.CurrentUser;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/accounts")
@Tag(name = "Счета")
public class AccountController {

    private final AccountService service;

    public AccountController(AccountService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Счета текущего пользователя")
    public List<AccountResponse> myAccounts() {
        return service.getAccounts(CurrentUser.id());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Открытие нового счёта")
    public AccountResponse create(@Valid @RequestBody CreateAccountRequest request) {
        return service.createAccount(CurrentUser.get(), request);
    }

    @GetMapping("/lookup")
    @Operation(summary = "Поиск счёта получателя по номеру (для подтверждения перевода)")
    public AccountLookupResponse lookup(@RequestParam String number) {
        return service.lookup(number);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Информация о счёте")
    public AccountResponse get(@PathVariable UUID id) {
        return service.getAccount(CurrentUser.get(), id);
    }

    @GetMapping("/{id}/balance")
    @Operation(summary = "Текущий баланс")
    public BalanceResponse balance(@PathVariable UUID id) {
        return service.getBalance(CurrentUser.get(), id);
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Заморозка, разморозка или закрытие счёта")
    public AccountResponse changeStatus(@PathVariable UUID id, @Valid @RequestBody ChangeAccountStatusRequest request) {
        return service.changeStatus(CurrentUser.get(), id, request.status());
    }
}
