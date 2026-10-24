package ru.bibank.account.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Журнал применённых операций — ключ идемпотентности для повторных вызовов.
 */
@Getter
@NoArgsConstructor
@Entity
@Table(name = "applied_operations")
public class AppliedOperation {

    @Id
    @Column(name = "operation_id")
    private UUID operationId;

    @Column(nullable = false, length = 50)
    private String type;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public AppliedOperation(UUID operationId, String type) {
        this.operationId = operationId;
        this.type = type;
        this.createdAt = Instant.now();
    }
}
