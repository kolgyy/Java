package ru.bibank.loan.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import ru.bibank.common.security.CurrentUser;
import ru.bibank.loan.dto.DepositProductResponse;
import ru.bibank.loan.dto.DepositResponse;
import ru.bibank.loan.dto.OpenDepositRequest;
import ru.bibank.loan.service.DepositService;
import ru.bibank.loan.service.ProductService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/deposits")
@Tag(name = "Вклады")
public class DepositController {

    private final ProductService productService;
    private final DepositService depositService;

    public DepositController(ProductService productService, DepositService depositService) {
        this.productService = productService;
        this.depositService = depositService;
    }

    @GetMapping("/products")
    @Operation(summary = "Накопительные продукты и ставки")
    public List<DepositProductResponse> products() {
        return productService.depositProducts();
    }

    @GetMapping
    @Operation(summary = "Мои вклады")
    public List<DepositResponse> mine() {
        return depositService.mine(CurrentUser.id());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Открытие вклада")
    public DepositResponse open(@Valid @RequestBody OpenDepositRequest request) {
        return depositService.open(CurrentUser.get(), request);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Вклад и история начислений")
    public DepositResponse get(@PathVariable UUID id) {
        return depositService.get(CurrentUser.get(), id);
    }

    @PostMapping("/{id}/close")
    @Operation(summary = "Закрытие вклада (досрочно — по пониженной ставке)")
    public DepositResponse close(@PathVariable UUID id) {
        return depositService.close(CurrentUser.get(), id);
    }
}
