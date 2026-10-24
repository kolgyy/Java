package ru.bibank.account.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.bibank.account.entity.Account;
import ru.bibank.account.entity.AccountStatus;
import ru.bibank.account.entity.AccountType;
import ru.bibank.account.entity.Currency;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AccountRepository extends JpaRepository<Account, UUID> {

    List<Account> findAllByOwnerIdOrderByCreatedAt(Long ownerId);

    long countByOwnerIdAndStatusNot(Long ownerId, AccountStatus status);

    Optional<Account> findByNumber(String number);

    boolean existsByNumber(String number);

    Optional<Account> findByTypeAndCurrency(AccountType type, Currency currency);

    /**
     * Блокировка строк в детерминированном порядке (по id) исключает взаимоблокировки
     * при встречных переводах A→B и B→A.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Account a WHERE a.id IN :ids ORDER BY a.id")
    List<Account> lockAllByIds(@Param("ids") Collection<UUID> ids);

    @Query("""
            SELECT a FROM Account a
            WHERE a.type <> ru.bibank.account.entity.AccountType.SYSTEM
              AND (:ownerId IS NULL OR a.ownerId = :ownerId)
              AND (:status IS NULL OR a.status = :status)
            """)
    Page<Account> search(@Param("ownerId") Long ownerId, @Param("status") AccountStatus status, Pageable pageable);

    @Query("""
            SELECT a.currency, COUNT(a), COALESCE(SUM(a.balance), 0) FROM Account a
            WHERE a.type <> ru.bibank.account.entity.AccountType.SYSTEM
            GROUP BY a.currency
            """)
    List<Object[]> statsByCurrency();

}
