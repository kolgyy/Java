package ru.bibank.account.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record OperationResult(UUID operationId, boolean alreadyApplied, List<AppliedPosting> postings) {

    public record AppliedPosting(UUID accountId, BigDecimal amount, BigDecimal balanceAfter) {
    }
}
