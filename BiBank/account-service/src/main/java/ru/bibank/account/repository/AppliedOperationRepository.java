package ru.bibank.account.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.bibank.account.entity.AppliedOperation;

import java.util.UUID;

public interface AppliedOperationRepository extends JpaRepository<AppliedOperation, UUID> {
}
