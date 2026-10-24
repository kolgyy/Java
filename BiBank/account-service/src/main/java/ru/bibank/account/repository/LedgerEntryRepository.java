package ru.bibank.account.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.bibank.account.entity.LedgerEntry;

import java.util.List;
import java.util.UUID;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, Long> {

    List<LedgerEntry> findAllByOperationId(UUID operationId);
}
