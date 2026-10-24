package ru.bibank.loan.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.bibank.loan.entity.Loan;
import ru.bibank.loan.entity.LoanStatus;

import java.util.List;
import java.util.UUID;

public interface LoanRepository extends JpaRepository<Loan, UUID> {

    List<Loan> findAllByUserIdOrderByCreatedAtDesc(Long userId);

    List<Loan> findAllByStatus(LoanStatus status);

    long countByStatus(LoanStatus status);
}
