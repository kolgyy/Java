package ru.bibank.loan.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.bibank.loan.entity.InterestAccrual;

import java.util.List;
import java.util.UUID;

public interface InterestAccrualRepository extends JpaRepository<InterestAccrual, UUID> {

    List<InterestAccrual> findTop100ByDepositIdOrderByAccrualDateDesc(UUID depositId);
}
