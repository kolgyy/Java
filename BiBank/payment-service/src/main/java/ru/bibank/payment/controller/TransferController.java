package ru.bibank.payment.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.bibank.common.security.CurrentUser;
import ru.bibank.common.web.PageResponse;
import ru.bibank.payment.dto.OperationResponse;
import ru.bibank.payment.dto.TopUpRequest;
import ru.bibank.payment.dto.TransferPreview;
import ru.bibank.payment.dto.TransferRequest;
import ru.bibank.payment.entity.OperationStatus;
import ru.bibank.payment.entity.OperationType;
import ru.bibank.payment.service.HistoryService;
import ru.bibank.payment.service.TransferService;

import java.util.UUID;

@RestController
@Tag(name = "Переводы")
public class TransferController {

    public static final String IDEMPOTENCY_HEADER = "Idempotency-Key";

    private final TransferService transferService;
    private final HistoryService historyService;

    public TransferController(TransferService transferService, HistoryService historyService) {
        this.transferService = transferService;
        this.historyService = historyService;
    }

    @PostMapping("/api/transfers/preview")
    @Operation(summary = "Проверка перевода перед подтверждением (получатель, курс, достаточность средств)")
    public TransferPreview preview(@Valid @RequestBody TransferRequest request) {
        return transferService.preview(CurrentUser.get(), request);
    }

    @PostMapping("/api/transfers")
    @Operation(summary = "Создание перевода",
            description = "201 — перевод создан, 200 — повтор запроса с тем же Idempotency-Key, "
                    + "202 — операция принята, но ещё не завершена")
    public ResponseEntity<OperationResponse> transfer(
            @Parameter(description = "Уникальный ключ запроса (UUID), защищает от повторного списания")
            @RequestHeader(value = IDEMPOTENCY_HEADER, required = false) String idempotencyKey,
            @Valid @RequestBody TransferRequest request) {
        var user = CurrentUser.get();
        var result = transferService.transfer(user, idempotencyKey, request);
        return respond(result, user.id());
    }

    @GetMapping("/api/transfers")
    @Operation(summary = "Переводы пользователя")
    public PageResponse<OperationResponse> list(@RequestParam(required = false) OperationStatus status,
                                                @RequestParam(defaultValue = "0") int page,
                                                @RequestParam(defaultValue = "20") int size) {
        var filter = new HistoryService.Filter(null, OperationType.TRANSFER, status, null, null);
        return historyService.history(CurrentUser.get(), filter, page, size);
    }

    @GetMapping("/api/transfers/{id}")
    @Operation(summary = "Информация о переводе")
    public OperationResponse get(@PathVariable UUID id) {
        return historyService.get(CurrentUser.get(), id, OperationType.TRANSFER);
    }

    @PostMapping("/api/top-ups")
    @Operation(summary = "Демо-пополнение счёта виртуальными средствами")
    public ResponseEntity<OperationResponse> topUp(
            @RequestHeader(value = IDEMPOTENCY_HEADER, required = false) String idempotencyKey,
            @Valid @RequestBody TopUpRequest request) {
        var user = CurrentUser.get();
        return respond(transferService.topUp(user, idempotencyKey, request), user.id());
    }

    private static ResponseEntity<OperationResponse> respond(TransferService.Result result, Long viewer) {
        HttpStatus status = result.operation().getStatus() == OperationStatus.PENDING ? HttpStatus.ACCEPTED
                : result.replayed() ? HttpStatus.OK : HttpStatus.CREATED;
        return ResponseEntity.status(status).body(OperationResponse.from(result.operation(), viewer));
    }
}
