package ru.bibank.payment.service;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.bibank.common.security.AuthenticatedUser;
import ru.bibank.common.web.ApiException;
import ru.bibank.common.web.PageResponse;
import ru.bibank.payment.dto.OperationResponse;
import ru.bibank.payment.dto.PaymentStatsResponse;
import ru.bibank.payment.entity.Operation;
import ru.bibank.payment.entity.OperationStatus;
import ru.bibank.payment.entity.OperationType;
import ru.bibank.payment.repository.OperationRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

/**
 * История операций формируется только из данных, сохранённых сервером.
 */
@Service
public class HistoryService {

    private static final ZoneId ZONE = ZoneId.of("Europe/Moscow");

    public record Filter(UUID accountId, OperationType type, OperationStatus status,
                         LocalDate from, LocalDate to) {
    }

    private final OperationRepository repository;

    public HistoryService(OperationRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public PageResponse<OperationResponse> history(AuthenticatedUser user, Filter filter, int page, int size) {
        Specification<Operation> spec = participant(user.id()).and(filtered(filter));
        var pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        return PageResponse.of(repository.findAll(spec, pageable), o -> OperationResponse.from(o, user.id()));
    }

    @Transactional(readOnly = true)
    public OperationResponse get(AuthenticatedUser user, UUID id, OperationType requiredType) {
        Operation op = repository.findById(id)
                .filter(o -> requiredType == null || o.getType() == requiredType)
                .filter(o -> user.isAdmin() || user.id().equals(o.getFromOwnerId()) || user.id().equals(o.getToOwnerId()))
                .orElseThrow(() -> ApiException.notFound("Операция не найдена"));
        return OperationResponse.from(op, user.id());
    }

    @Transactional(readOnly = true)
    public PageResponse<OperationResponse> adminSearch(Filter filter, int page, int size) {
        var pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        return PageResponse.of(repository.findAll(filtered(filter), pageable), o -> OperationResponse.from(o, o.getFromOwnerId()));
    }

    @Transactional(readOnly = true)
    public PaymentStatsResponse stats() {
        Map<String, Map<String, Long>> byType = new TreeMap<>();
        for (Object[] row : repository.countByTypeAndStatus()) {
            byType.computeIfAbsent(((OperationType) row[0]).name(), k -> new TreeMap<>())
                    .put(((OperationStatus) row[1]).name(), ((Number) row[2]).longValue());
        }
        Map<String, BigDecimal> volume = new TreeMap<>();
        for (Object[] row : repository.transferVolumeByCurrency()) {
            volume.put((String) row[0], (BigDecimal) row[1]);
        }
        return new PaymentStatsResponse(repository.count(),
                repository.countByStatus(OperationStatus.PENDING),
                repository.countByStatus(OperationStatus.COMPLETED),
                repository.countByStatus(OperationStatus.FAILED),
                byType, volume);
    }

    private static Specification<Operation> participant(Long userId) {
        return (root, q, cb) -> cb.or(cb.equal(root.get("fromOwnerId"), userId), cb.equal(root.get("toOwnerId"), userId));
    }

    private static Specification<Operation> filtered(Filter f) {
        return (root, q, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (f.accountId() != null) {
                p.add(cb.or(cb.equal(root.get("fromAccountId"), f.accountId()),
                        cb.equal(root.get("toAccountId"), f.accountId())));
            }
            if (f.type() != null) {
                p.add(cb.equal(root.get("type"), f.type()));
            }
            if (f.status() != null) {
                p.add(cb.equal(root.get("status"), f.status()));
            }
            if (f.from() != null) {
                p.add(cb.greaterThanOrEqualTo(root.get("createdAt"), f.from().atStartOfDay(ZONE).toInstant()));
            }
            if (f.to() != null) {
                p.add(cb.lessThan(root.get("createdAt"), f.to().plusDays(1).atStartOfDay(ZONE).toInstant()));
            }
            return cb.and(p.toArray(Predicate[]::new));
        };
    }
}
