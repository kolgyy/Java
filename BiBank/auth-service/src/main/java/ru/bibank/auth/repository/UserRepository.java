package ru.bibank.auth.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.bibank.auth.entity.Role;
import ru.bibank.auth.entity.User;
import ru.bibank.auth.entity.UserStatus;

import java.time.Instant;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    @Query("""
            SELECT u FROM User u
            WHERE (:search IS NULL
                   OR LOWER(u.email) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%'))
                   OR LOWER(u.fullName) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')))
              AND (:status IS NULL OR u.status = :status)
            """)
    Page<User> search(@Param("search") String search, @Param("status") UserStatus status, Pageable pageable);

    long countByStatus(UserStatus status);

    long countByRole(Role role);

    long countByCreatedAtAfter(Instant since);
}
