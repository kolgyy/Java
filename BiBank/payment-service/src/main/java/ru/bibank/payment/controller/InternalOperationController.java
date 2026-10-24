package ru.bibank.payment.controller;

import io.swagger.v3.oas.annotations.Hidden;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import ru.bibank.payment.dto.InternalOperationRequest;
import ru.bibank.payment.dto.OperationResponse;
import ru.bibank.payment.service.TransferService;

@Hidden
@RestController
@RequestMapping("/internal/operations")
public class InternalOperationController {

    private final TransferService transferService;

    public InternalOperationController(TransferService transferService) {
        this.transferService = transferService;
    }

    @PostMapping
    public OperationResponse apply(@Valid @RequestBody InternalOperationRequest request) {
        var op = transferService.internal(request);
        return OperationResponse.from(op, request.userId());
    }
}
