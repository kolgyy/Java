package ru.bibank.loan.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.bibank.loan.entity.ApplicationStatus;
import ru.bibank.loan.entity.LoanApplication;

import java.util.List;
import java.util.UUID;

public interface LoanApplicationRepository extends JpaRepository<LoanApplication, UUID> {

    List<LoanApplication> findAllByUserIdOrderByCreatedAtDesc(Long userId);

    Page<LoanApplication> findAllByStatus(ApplicationStatus status, Pageable pageable);

    long countByUserIdAndStatus(Long userId, ApplicationStatus status);

    long countByStatus(ApplicationStatus status);
}
