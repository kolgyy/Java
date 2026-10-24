package ru.bibank.payment.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import ru.bibank.common.web.PageResponse;
import ru.bibank.payment.dto.OperationResponse;
import ru.bibank.payment.dto.PaymentStatsResponse;
import ru.bibank.payment.entity.OperationStatus;
import ru.bibank.payment.entity.OperationType;
import ru.bibank.payment.service.HistoryService;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/transactions")
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
@Tag(name = "Администрирование операций")
public class AdminPaymentController {

    private final HistoryService historyService;

    public AdminPaymentController(HistoryService historyService) {
        this.historyService = historyService;
    }

    @GetMapping
    @Operation(summary = "Все операции")
    public PageResponse<OperationResponse> list(
            @RequestParam(required = false) UUID accountId,
            @RequestParam(required = false) OperationType type,
            @RequestParam(required = false) OperationStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return historyService.adminSearch(new HistoryService.Filter(accountId, type, status, from, to), page, size);
    }

    @GetMapping("/stats")
    @Operation(summary = "Сводка по операциям")
    public PaymentStatsResponse stats() {
        return historyService.stats();
    }
}
