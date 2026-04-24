package com.bbss.transaction.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Aggregated transaction statistics for a tenant over a rolling 24-hour window.
 */
@Getter
@Builder
public class TransactionStatsResponse {

    /** Tenant identifier these statistics belong to. */
    private final String tenantId;

    /** Total number of transactions ever recorded for the tenant. */
    private final long totalTransactions;

    /** Total transaction amount ever recorded for the tenant. */
    private final BigDecimal totalAmount;

    /** Total number of transactions currently in SUBMITTED state. */
    private final long totalSubmitted;

    /** Total number of transactions that passed fraud checks. */
    private final long totalVerified;

    /** Total number of transactions automatically blocked by the fraud engine. */
    private final long totalBlocked;

    /** Total number of transactions placed under manual fraud review. */
    private final long totalFraudHold;

    /** Total number of transactions that reached the COMPLETED terminal state. */
    private final long totalCompleted;

    /** Total number of transactions that reached the FAILED terminal state. */
    private final long totalFailed;

    /** Aggregate counts by business status. */
    private final Map<String, Long> statusCounts;

    /** Aggregate counts by ledger status. */
    private final Map<String, Long> ledgerStatusCounts;

    /** Aggregate counts by verification status. */
    private final Map<String, Long> verificationStatusCounts;

    /** Aggregate counts by fraud risk level. */
    private final Map<String, Long> fraudRiskDistribution;

    /** Daily volume series for the requested reporting window. */
    private final List<DailyVolumePoint> dailyVolume;

    /** Total transaction count within the requested reporting window. */
    private final long windowTransactions;

    /** Total transaction amount within the requested reporting window. */
    private final BigDecimal windowAmount;

    /** Start of the reporting window (inclusive). */
    private final LocalDateTime from;

    /** End of the reporting window (inclusive). */
    private final LocalDateTime to;

    @Getter
    @Builder
    public static class DailyVolumePoint {
        private final String date;
        private final String label;
        private final long transactionCount;
        private final BigDecimal totalAmount;
    }
}
