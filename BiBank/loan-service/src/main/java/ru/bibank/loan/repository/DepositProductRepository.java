package ru.bibank.loan.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.bibank.loan.entity.DepositProduct;

import java.util.List;
import java.util.UUID;

public interface DepositProductRepository extends JpaRepository<DepositProduct, UUID> {
    List<DepositProduct> findAllByOrderByAnnualRateDesc();
}
