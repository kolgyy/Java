package ru.bibank.payment.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.bibank.payment.entity.Operation;
import ru.bibank.payment.entity.OperationStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OperationRepository extends JpaRepository<Operation, UUID>, JpaSpecificationExecutor<Operation> {

    Optional<Operation> findByInitiatorIdAndIdempotencyKey(Long initiatorId, String idempotencyKey);

    @Query("""
            SELECT o FROM Operation o
            WHERE o.status = ru.bibank.payment.entity.OperationStatus.PENDING
              AND o.updatedAt < :before AND o.attempts < :maxAttempts
            ORDER BY o.createdAt
            """)
    List<Operation> findStalePending(@Param("before") Instant before, @Param("maxAttempts") int maxAttempts);

    long countByStatus(OperationStatus status);

    @Query("SELECT o.type, o.status, COUNT(o) FROM Operation o GROUP BY o.type, o.status")
    List<Object[]> countByTypeAndStatus();

    @Query("""
            SELECT o.currency, COALESCE(SUM(o.amount), 0) FROM Operation o
            WHERE o.status = ru.bibank.payment.entity.OperationStatus.COMPLETED
              AND o.type = ru.bibank.payment.entity.OperationType.TRANSFER
            GROUP BY o.currency
            """)
    List<Object[]> transferVolumeByCurrency();
}
