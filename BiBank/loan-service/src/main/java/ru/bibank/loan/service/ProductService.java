package ru.bibank.loan.service;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.bibank.common.web.ApiException;
import ru.bibank.loan.dto.DepositProductResponse;
import ru.bibank.loan.dto.LoanProductResponse;
import ru.bibank.loan.dto.UpdateProductRequest;
import ru.bibank.loan.entity.DepositProduct;
import ru.bibank.loan.entity.LoanProduct;
import ru.bibank.loan.repository.DepositProductRepository;
import ru.bibank.loan.repository.LoanProductRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Каталог продуктов. Списки кэшируются в Redis (TTL 10 минут) и инвалидируются при изменении продукта.
 * Бизнес-операции всегда читают продукт из PostgreSQL.
 */
@Service
public class ProductService {

    public static final String LOAN_PRODUCTS = "loan-products";
    public static final String DEPOSIT_PRODUCTS = "deposit-products";

    private final LoanProductRepository loanProducts;
    private final DepositProductRepository depositProducts;

    public ProductService(LoanProductRepository loanProducts, DepositProductRepository depositProducts) {
        this.loanProducts = loanProducts;
        this.depositProducts = depositProducts;
    }

    @Cacheable(cacheNames = LOAN_PRODUCTS, key = "'all'")
    @Transactional(readOnly = true)
    public List<LoanProductResponse> loanProducts() {
        return new ArrayList<>(loanProducts.findAllByOrderByAnnualRate().stream().map(LoanProductResponse::from).toList());
    }

    @Cacheable(cacheNames = DEPOSIT_PRODUCTS, key = "'all'")
    @Transactional(readOnly = true)
    public List<DepositProductResponse> depositProducts() {
        return new ArrayList<>(depositProducts.findAllByOrderByAnnualRateDesc().stream()
                .map(DepositProductResponse::from).toList());
    }

    @Transactional(readOnly = true)
    public LoanProduct activeLoanProduct(UUID id) {
        LoanProduct product = loanProducts.findById(id).orElseThrow(() -> ApiException.notFound("Продукт не найден"));
        if (!product.isActive()) {
            throw ApiException.unprocessable("PRODUCT_INACTIVE", "Продукт недоступен для оформления");
        }
        return product;
    }

    @Transactional(readOnly = true)
    public DepositProduct activeDepositProduct(UUID id) {
        DepositProduct product = depositProducts.findById(id).orElseThrow(() -> ApiException.notFound("Продукт не найден"));
        if (!product.isActive()) {
            throw ApiException.unprocessable("PRODUCT_INACTIVE", "Продукт недоступен для оформления");
        }
        return product;
    }

    @CacheEvict(cacheNames = LOAN_PRODUCTS, allEntries = true)
    @Transactional
    public LoanProductResponse updateLoanProduct(UUID id, UpdateProductRequest request) {
        LoanProduct p = loanProducts.findById(id).orElseThrow(() -> ApiException.notFound("Продукт не найден"));
        if (request.annualRate() != null) {
            p.setAnnualRate(request.annualRate());
        }
        if (request.active() != null) {
            p.setActive(request.active());
        }
        return LoanProductResponse.from(p);
    }

    @CacheEvict(cacheNames = DEPOSIT_PRODUCTS, allEntries = true)
    @Transactional
    public DepositProductResponse updateDepositProduct(UUID id, UpdateProductRequest request) {
        DepositProduct p = depositProducts.findById(id).orElseThrow(() -> ApiException.notFound("Продукт не найден"));
        if (request.annualRate() != null) {
            p.setAnnualRate(request.annualRate());
        }
        if (request.active() != null) {
            p.setActive(request.active());
        }
        return DepositProductResponse.from(p);
    }
}
