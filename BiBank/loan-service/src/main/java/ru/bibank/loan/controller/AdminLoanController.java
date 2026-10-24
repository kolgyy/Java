package ru.bibank.loan.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import ru.bibank.common.security.CurrentUser;
import ru.bibank.common.web.PageResponse;
import ru.bibank.loan.dto.*;
import ru.bibank.loan.entity.ApplicationStatus;
import ru.bibank.loan.entity.DepositStatus;
import ru.bibank.loan.entity.LoanStatus;
import ru.bibank.loan.repository.DepositRepository;
import ru.bibank.loan.repository.LoanApplicationRepository;
import ru.bibank.loan.repository.LoanRepository;
import ru.bibank.loan.service.BackgroundJobs;
import ru.bibank.loan.service.LoanApplicationService;
import ru.bibank.loan.service.ProductService;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
@Validated
@Tag(name = "Администрирование кредитов и вкладов")
public class AdminLoanController {

    private final LoanApplicationService applicationService;
    private final ProductService productService;
    private final BackgroundJobs jobs;
    private final LoanApplicationRepository applications;
    private final LoanRepository loans;
    private final DepositRepository deposits;

    public AdminLoanController(LoanApplicationService applicationService, ProductService productService,
                               BackgroundJobs jobs, LoanApplicationRepository applications, LoanRepository loans,
                               DepositRepository deposits) {
        this.applicationService = applicationService;
        this.productService = productService;
        this.jobs = jobs;
        this.applications = applications;
        this.loans = loans;
        this.deposits = deposits;
    }

    @GetMapping("/loans/applications")
    @Operation(summary = "Заявки на кредит")
    public PageResponse<ApplicationResponse> applications(@RequestParam(required = false) ApplicationStatus status,
                                                          @RequestParam(defaultValue = "0") int page,
                                                          @RequestParam(defaultValue = "20") int size) {
        return applicationService.adminList(status, page, size);
    }

    @PostMapping("/loans/applications/{id}/approve")
    @Operation(summary = "Одобрить заявку и выдать кредит")
    public ApplicationResponse approve(@PathVariable UUID id, @Valid @RequestBody(required = false) DecisionRequest request) {
        return applicationService.approve(id, CurrentUser.id(), request == null ? null : request.reason());
    }

    @PostMapping("/loans/applications/{id}/reject")
    @Operation(summary = "Отклонить заявку")
    public ApplicationResponse reject(@PathVariable UUID id, @Valid @RequestBody(required = false) DecisionRequest request) {
        return applicationService.reject(id, CurrentUser.id(), request == null ? null : request.reason());
    }

    @PatchMapping("/loans/products/{id}")
    @Operation(summary = "Изменить ставку или доступность кредитного продукта (инвалидирует кэш)")
    public LoanProductResponse updateLoanProduct(@PathVariable UUID id, @Valid @RequestBody UpdateProductRequest request) {
        return productService.updateLoanProduct(id, request);
    }

    @PatchMapping("/deposits/products/{id}")
    @Operation(summary = "Изменить ставку или доступность вклада (инвалидирует кэш)")
    public DepositProductResponse updateDepositProduct(@PathVariable UUID id, @Valid @RequestBody UpdateProductRequest request) {
        return productService.updateDepositProduct(id, request);
    }

    @PostMapping("/deposits/simulate-days")
    @Operation(summary = "Демо: начислить проценты за N дней вперёд по всем активным вкладам")
    public Map<String, Integer> simulate(@RequestParam(defaultValue = "1") @Min(1) @Max(400) int days) {
        return Map.of("deposits", jobs.simulateDays(days));
    }

    @GetMapping("/loans/stats")
    @Operation(summary = "Сводка по кредитам и вкладам")
    public LoanStatsResponse stats() {
        return new LoanStatsResponse(
                applications.countByStatus(ApplicationStatus.SUBMITTED),
                applications.countByStatus(ApplicationStatus.APPROVED),
                applications.countByStatus(ApplicationStatus.REJECTED),
                loans.countByStatus(LoanStatus.ACTIVE),
                loans.countByStatus(LoanStatus.CLOSED),
                deposits.countByStatus(DepositStatus.ACTIVE),
                deposits.countByStatus(DepositStatus.CLOSED));
    }
}
