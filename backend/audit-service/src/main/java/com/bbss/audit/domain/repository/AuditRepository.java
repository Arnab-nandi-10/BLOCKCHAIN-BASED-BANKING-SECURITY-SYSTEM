package com.bbss.audit.domain.repository;

import com.bbss.audit.domain.model.AuditEntry;
import com.bbss.audit.domain.model.AuditStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link AuditEntry} persistence.
 *
 * <p>All finder methods are scoped to a specific {@code tenantId} to enforce
 * tenant isolation at the data-access layer.  The only exception is
 * {@link #findByStatus} and {@link #findByStatusIn}, which are used exclusively
 * by the internal retry scheduler and run in a privileged system context.
 */
@Repository
public interface AuditRepository extends JpaRepository<AuditEntry, UUID>, JpaSpecificationExecutor<AuditEntry> {

    // ── Single-record lookups ─────────────────────────────────────────────────

    /**
     * Finds an audit entry by its business-level identifier.
     * Used for idempotency checking and direct API lookups.
     */
    Optional<AuditEntry> findByAuditId(String auditId);

    // ── Paginated tenant-scoped queries ───────────────────────────────────────

    /**
     * Returns all audit entries for a tenant, most recent first.
     */
    Page<AuditEntry> findByTenantIdOrderByOccurredAtDesc(String tenantId, Pageable pageable);

    /**
     * Returns all audit entries for a specific entity instance within a tenant.
     * Useful for retrieving the full history of a single transaction or user.
     */
    Page<AuditEntry> findByTenantIdAndEntityTypeAndEntityId(
            String tenantId,
            String entityType,
            String entityId,
            Pageable pageable);

    /**
     * Returns all audit entries for a given action verb within a tenant.
     * Useful for querying all fraud-detection events, all logins, etc.
     */
    Page<AuditEntry> findByTenantIdAndAction(
            String tenantId,
            String action,
            Pageable pageable);

    /**
     * Returns all audit entries for a tenant that occurred within the given
     * time window (inclusive on both bounds).
     */
    Page<AuditEntry> findByTenantIdAndOccurredAtBetween(
            String tenantId,
            LocalDateTime from,
            LocalDateTime to,
            Pageable pageable);

    // ── System-level queries (retry scheduler) ────────────────────────────────

    /** Returns all entries with the given status across all tenants. */
    List<AuditEntry> findByStatus(AuditStatus status);

    /**
     * Returns all entries whose status is one of the supplied values.
     * Used by the retry scheduler to fetch both PENDING and FAILED records in
     * a single query.
     */
    List<AuditEntry> findByStatusIn(List<AuditStatus> statuses);

    // ── Aggregation / summary ─────────────────────────────────────────────────

    /** Counts all entries for a tenant regardless of time window. */
    long countByTenantId(String tenantId);

    /**
     * Counts entries for a tenant that occurred within the given time window.
     * Used for the "last 24 h" metric in the audit summary.
     */
    long countByTenantIdAndOccurredAtBetween(
            String tenantId,
            LocalDateTime from,
            LocalDateTime to);

    /** Counts entries for a tenant with the given status. */
    long countByTenantIdAndStatus(String tenantId, AuditStatus status);

    /**
     * Returns {@code (action, count)} pairs for all actions recorded for a
     * tenant, enabling the action breakdown chart in the audit dashboard.
     *
     * @param tenantId the tenant identifier
     * @return list of 2-element {@code Object[]} arrays: {@code [action, count]}
     */
    @Query("SELECT a.action, COUNT(a) FROM AuditEntry a WHERE a.tenantId = :tenantId GROUP BY a.action ORDER BY COUNT(a) DESC")
    List<Object[]> findActionBreakdownByTenantId(@Param("tenantId") String tenantId);
}
