package ru.bibank.loan.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.bibank.loan.entity.LoanPayment;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface LoanPaymentRepository extends JpaRepository<LoanPayment, UUID> {

    List<LoanPayment> findAllByLoanIdOrderByCreatedAtDesc(UUID loanId);

    List<LoanPayment> findAllByStatusAndCreatedAtBefore(LoanPayment.Status status, Instant before);

    boolean existsByLoanIdAndStatus(UUID loanId, LoanPayment.Status status);
}
