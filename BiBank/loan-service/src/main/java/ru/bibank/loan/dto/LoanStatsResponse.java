package ru.bibank.loan.dto;

public record LoanStatsResponse(
        long applicationsSubmitted,
        long applicationsApproved,
        long applicationsRejected,
        long activeLoans,
        long closedLoans,
        long activeDeposits,
        long closedDeposits
) {
}
