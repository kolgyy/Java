package ru.bibank.account.controller;

import io.swagger.v3.oas.annotations.Hidden;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import ru.bibank.account.dto.InternalAccountResponse;
import ru.bibank.account.dto.OperationRequest;
import ru.bibank.account.dto.OperationResult;
import ru.bibank.account.entity.Currency;
import ru.bibank.account.service.AccountService;
import ru.bibank.account.service.LedgerService;

import java.util.UUID;

/**
 * Межсервисный API. Недоступен через API Gateway, требует X-Internal-Token.
 */
@Hidden
@RestController
@RequestMapping("/internal")
public class InternalAccountController {

    private final AccountService accountService;
    private final LedgerService ledgerService;

    public InternalAccountController(AccountService accountService, LedgerService ledgerService) {
        this.accountService = accountService;
        this.ledgerService = ledgerService;
    }

    @GetMapping("/accounts/{id}")
    public InternalAccountResponse byId(@PathVariable UUID id) {
        return accountService.internalById(id);
    }

    @GetMapping("/accounts/by-number/{number}")
    public InternalAccountResponse byNumber(@PathVariable String number) {
        return accountService.internalByNumber(number);
    }

    @GetMapping("/accounts/system/{currency}")
    public InternalAccountResponse system(@PathVariable Currency currency) {
        return accountService.systemAccount(currency);
    }

    @PostMapping("/operations")
    public OperationResult apply(@Valid @RequestBody OperationRequest request) {
        return ledgerService.apply(request);
    }
}
