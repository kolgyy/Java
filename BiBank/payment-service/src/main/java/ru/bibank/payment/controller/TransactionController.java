package ru.bibank.payment.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import ru.bibank.common.security.CurrentUser;
import ru.bibank.common.web.PageResponse;
import ru.bibank.payment.dto.ExchangeRatesResponse;
import ru.bibank.payment.dto.OperationResponse;
import ru.bibank.payment.entity.OperationStatus;
import ru.bibank.payment.entity.OperationType;
import ru.bibank.payment.service.ExchangeRateService;
import ru.bibank.payment.service.HistoryService;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@Tag(name = "История операций")
public class TransactionController {

    private final HistoryService historyService;
    private final ExchangeRateService exchangeRateService;

    public TransactionController(HistoryService historyService, ExchangeRateService exchangeRateService) {
        this.historyService = historyService;
        this.exchangeRateService = exchangeRateService;
    }

    @GetMapping("/api/transactions")
    @Operation(summary = "История операций с фильтрами по счёту, типу, статусу и периоду")
    public PageResponse<OperationResponse> history(
            @RequestParam(required = false) UUID accountId,
            @RequestParam(required = false) OperationType type,
            @RequestParam(required = false) OperationStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        var filter = new HistoryService.Filter(accountId, type, status, from, to);
        return historyService.history(CurrentUser.get(), filter, page, size);
    }

    @GetMapping("/api/transactions/{id}")
    @Operation(summary = "Подробности операции")
    public OperationResponse get(@PathVariable UUID id) {
        return historyService.get(CurrentUser.get(), id, null);
    }

    @GetMapping("/api/exchange-rates")
    @Operation(summary = "Курсы валют к рублю (виртуальные)")
    public ExchangeRatesResponse rates() {
        return exchangeRateService.rates();
    }
}
