package ru.bibank.loan.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import ru.bibank.common.security.CurrentUser;
import ru.bibank.loan.dto.*;
import ru.bibank.loan.service.LoanApplicationService;
import ru.bibank.loan.service.LoanService;
import ru.bibank.loan.service.ProductService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/loans")
@Tag(name = "Кредиты")
public class LoanController {

    private final ProductService productService;
    private final LoanApplicationService applicationService;
    private final LoanService loanService;

    public LoanController(ProductService productService, LoanApplicationService applicationService,
                          LoanService loanService) {
        this.productService = productService;
        this.applicationService = applicationService;
        this.loanService = loanService;
    }

    @GetMapping("/products")
    @Operation(summary = "Кредитные предложения")
    public List<LoanProductResponse> products() {
        return productService.loanProducts();
    }

    @PostMapping("/calculate")
    @Operation(summary = "Расчёт платежа и графика погашения")
    public LoanCalculationResponse calculate(@Valid @RequestBody LoanCalculationRequest request) {
        return applicationService.calculate(request);
    }

    @PostMapping("/applications")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Подача кредитной заявки")
    public ApplicationResponse apply(@Valid @RequestBody CreateApplicationRequest request) {
        return applicationService.submit(CurrentUser.get(), request);
    }

    @GetMapping("/applications")
    @Operation(summary = "Мои заявки")
    public List<ApplicationResponse> applications() {
        return applicationService.mine(CurrentUser.id());
    }

    @GetMapping("/applications/{id}")
    @Operation(summary = "Статус заявки")
    public ApplicationResponse application(@PathVariable UUID id) {
        return applicationService.get(CurrentUser.get(), id);
    }

    @PostMapping("/applications/{id}/cancel")
    @Operation(summary = "Отмена заявки, которая ещё не рассмотрена")
    public ApplicationResponse cancel(@PathVariable UUID id) {
        return applicationService.cancel(CurrentUser.get(), id);
    }

    @GetMapping
    @Operation(summary = "Мои кредиты")
    public List<LoanResponse> loans() {
        return loanService.mine(CurrentUser.id());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Кредит: задолженность, график, платежи")
    public LoanResponse loan(@PathVariable UUID id) {
        return loanService.get(CurrentUser.get(), id);
    }

    @PostMapping("/{id}/payments")
    @Operation(summary = "Погашение: очередной платёж или полное досрочное")
    public PaymentResponse repay(@PathVariable UUID id, @Valid @RequestBody RepayRequest request) {
        return loanService.repay(CurrentUser.get(), id, request);
    }
}
