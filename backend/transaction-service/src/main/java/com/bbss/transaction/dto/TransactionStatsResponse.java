package com.bbss.transaction.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * Aggregated transaction statistics for a tenant over a rolling 24-hour window.
 */
@Getter
@Builder
public class TransactionStatsResponse {

    /** Tenant identifier these statistics belong to. */
    private final String tenantId;

    /** Total number of transactions submitted in the period. */
    private final long totalSubmitted;

    /** Total number of transactions that were verified and written to the blockchain. */
    private final long totalVerified;

    /** Total number of transactions automatically blocked by the fraud engine. */
    private final long totalBlocked;

    /** Total number of transactions placed under manual fraud review. */
    private final long totalFraudHold;

    /** Total number of transactions that reached the COMPLETED terminal state. */
    private final long totalCompleted;

    /** Total number of transactions that reached the FAILED terminal state. */
    private final long totalFailed;

    /** Start of the reporting window (inclusive). */
    private final LocalDateTime from;

    /** End of the reporting window (inclusive). */
    private final LocalDateTime to;
}
