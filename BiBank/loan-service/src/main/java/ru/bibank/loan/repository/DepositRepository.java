package ru.bibank.loan.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.bibank.loan.entity.Deposit;
import ru.bibank.loan.entity.DepositStatus;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface DepositRepository extends JpaRepository<Deposit, UUID> {

    List<Deposit> findAllByUserIdOrderByCreatedAtDesc(Long userId);

    List<Deposit> findAllByStatus(DepositStatus status);

    List<Deposit> findAllByStatusIn(Collection<DepositStatus> statuses);

    long countByStatus(DepositStatus status);
}
