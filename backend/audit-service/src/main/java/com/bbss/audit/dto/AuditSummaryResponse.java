package com.bbss.audit.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Aggregated audit statistics for a single tenant.
 *
 * <p>Returned by {@code GET /api/v1/audit/summary} to power dashboards and
 * compliance reports.
 */
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AuditSummaryResponse {

    /** Tenant this summary was computed for. */
    private final String tenantId;

    /** Total number of audit entries ever recorded for the tenant. */
    private final long totalEntries;

    /**
     * Number of audit entries created in the 24-hour window ending at
     * {@link #generatedAt}.
     */
    private final long last24hEntries;

    /**
     * Number of entries in {@link com.bbss.audit.domain.model.AuditStatus#PENDING}
     * state (i.e. not yet anchored on the blockchain).
     */
    private final long pendingEntries;

    /**
     * Number of entries in {@link com.bbss.audit.domain.model.AuditStatus#FAILED}
     * state (i.e. blockchain commit has failed at least once and is eligible
     * for retry).
     */
    private final long failedEntries;

    /**
     * Distribution of audit entries by action verb for the tenant.
     * Keys are action strings (e.g. {@code "FRAUD_DETECTED"}), values are
     * entry counts, ordered by count descending.
     */
    private final Map<String, Long> actionBreakdown;

    /**
     * Server-side instant at which this summary was assembled.
     * Consumers should use this as the "as of" timestamp rather than
     * the current time.
     */
    private final LocalDateTime generatedAt;
}
