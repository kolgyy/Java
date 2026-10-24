package ru.bibank.loan.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.bibank.loan.entity.LoanProduct;

import java.util.List;
import java.util.UUID;

public interface LoanProductRepository extends JpaRepository<LoanProduct, UUID> {
    List<LoanProduct> findAllByOrderByAnnualRate();
}
