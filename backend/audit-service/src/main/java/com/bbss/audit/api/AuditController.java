package com.bbss.audit.api;

import com.bbss.audit.dto.AuditResponse;
import com.bbss.audit.dto.AuditSummaryResponse;
import com.bbss.audit.service.AuditService;
import com.bbss.shared.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

/**
 * REST API for querying the immutable audit trail.
 *
 * <p>All endpoints require a valid JWT and the {@code X-Tenant-ID} request
 * header.  Role requirements:
 * <ul>
 *   <li>{@code ROLE_ADMIN}   – full access</li>
 *   <li>{@code ROLE_ANALYST} – full read access</li>
 *   <li>{@code ROLE_VIEWER}  – full read access</li>
 * </ul>
 *
 * <p>All responses are wrapped in the shared {@link ApiResponse} envelope.
 */
@RestController
@RequestMapping("/api/v1/audit")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Audit Trail", description = "Immutable, blockchain-anchored audit log queries")
public class AuditController {

    private final AuditService auditService;

    // ── List all ──────────────────────────────────────────────────────────────

    /**
     * Returns a paginated list of all audit entries for the requesting tenant,
     * ordered most-recent first.
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'ANALYST', 'VIEWER')")
    @Operation(summary = "List audit entries", description = "Returns paginated audit entries for the tenant, most recent first")
    public ResponseEntity<ApiResponse<Page<AuditResponse>>> listAuditEntries(
            @Parameter(description = "Tenant identifier", required = true)
            @RequestHeader("X-Tenant-ID") String tenantId,
            @PageableDefault(size = 20, sort = "occurredAt") Pageable pageable) {

        log.debug("GET /api/v1/audit tenantId={} page={}", tenantId, pageable.getPageNumber());
        Page<AuditResponse> page = auditService.listAuditEntries(tenantId, pageable);
        return ResponseEntity.ok(ApiResponse.success(page));
    }

    // ── Single entry ──────────────────────────────────────────────────────────

    /**
     * Returns a single audit entry by its business-level audit ID.
     */
    @GetMapping("/{auditId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'ANALYST', 'VIEWER')")
    @Operation(summary = "Get audit entry", description = "Returns a single audit entry by its business ID")
    public ResponseEntity<ApiResponse<AuditResponse>> getAuditEntry(
            @Parameter(description = "Business-level audit identifier", required = true)
            @PathVariable String auditId,
            @Parameter(description = "Tenant identifier", required = true)
            @RequestHeader("X-Tenant-ID") String tenantId) {

        log.debug("GET /api/v1/audit/{} tenantId={}", auditId, tenantId);
        AuditResponse response = auditService.getAuditEntry(auditId, tenantId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // ── By entity ─────────────────────────────────────────────────────────────

    /**
     * Returns a paginated audit history for a specific entity instance
     * (e.g. all audit records for a particular transaction).
     */
    @GetMapping("/entity/{entityType}/{entityId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'ANALYST', 'VIEWER')")
    @Operation(summary = "List by entity", description = "Returns the full audit history of a specific entity")
    public ResponseEntity<ApiResponse<Page<AuditResponse>>> listByEntityId(
            @Parameter(description = "Entity domain type, e.g. TRANSACTION / USER / TENANT", required = true)
            @PathVariable String entityType,
            @Parameter(description = "Entity primary key or external identifier", required = true)
            @PathVariable String entityId,
            @Parameter(description = "Tenant identifier", required = true)
            @RequestHeader("X-Tenant-ID") String tenantId,
            @PageableDefault(size = 20) Pageable pageable) {

        log.debug("GET /api/v1/audit/entity/{}/{} tenantId={}", entityType, entityId, tenantId);
        Page<AuditResponse> page = auditService.listByEntityId(tenantId, entityType, entityId, pageable);
        return ResponseEntity.ok(ApiResponse.success(page));
    }

    // ── By action ─────────────────────────────────────────────────────────────

    /**
     * Returns all audit entries for a given action verb (e.g.
     * {@code FRAUD_DETECTED}, {@code USER_LOGIN}).
     */
    @GetMapping("/action/{action}")
    @PreAuthorize("hasAnyRole('ADMIN', 'ANALYST', 'VIEWER')")
    @Operation(summary = "List by action", description = "Returns all audit entries for a specific action verb")
    public ResponseEntity<ApiResponse<Page<AuditResponse>>> listByAction(
            @Parameter(description = "Action verb, e.g. TRANSACTION_VERIFIED / FRAUD_DETECTED", required = true)
            @PathVariable String action,
            @Parameter(description = "Tenant identifier", required = true)
            @RequestHeader("X-Tenant-ID") String tenantId,
            @PageableDefault(size = 20) Pageable pageable) {

        log.debug("GET /api/v1/audit/action/{} tenantId={}", action, tenantId);
        Page<AuditResponse> page = auditService.listByAction(tenantId, action, pageable);
        return ResponseEntity.ok(ApiResponse.success(page));
    }

    // ── By date range ─────────────────────────────────────────────────────────

    /**
     * Returns all audit entries within an inclusive time window.
     *
     * <p>Both {@code from} and {@code to} must be supplied in ISO-8601
     * {@code LocalDateTime} format: {@code yyyy-MM-ddTHH:mm:ss}
     * (e.g. {@code 2025-01-15T00:00:00}).
     */
    @GetMapping("/date-range")
    @PreAuthorize("hasAnyRole('ADMIN', 'ANALYST', 'VIEWER')")
    @Operation(summary = "List by date range", description = "Returns audit entries within an inclusive time window")
    public ResponseEntity<ApiResponse<Page<AuditResponse>>> listByDateRange(
            @Parameter(description = "Tenant identifier", required = true)
            @RequestHeader("X-Tenant-ID") String tenantId,
            @Parameter(description = "Range start (ISO-8601 LocalDateTime)", required = true)
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @Parameter(description = "Range end (ISO-8601 LocalDateTime)", required = true)
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @PageableDefault(size = 20) Pageable pageable) {

        log.debug("GET /api/v1/audit/date-range tenantId={} from={} to={}", tenantId, from, to);
        Page<AuditResponse> page = auditService.listByDateRange(tenantId, from, to, pageable);
        return ResponseEntity.ok(ApiResponse.success(page));
    }

    // ── Summary ───────────────────────────────────────────────────────────────

    /**
     * Returns an aggregated audit summary: total entry counts, last-24-h
     * activity, pending/failed counts, and a per-action breakdown.
     */
    @GetMapping("/summary")
    @PreAuthorize("hasAnyRole('ADMIN', 'ANALYST', 'VIEWER')")
    @Operation(summary = "Audit summary", description = "Returns aggregated audit statistics for the tenant")
    public ResponseEntity<ApiResponse<AuditSummaryResponse>> getAuditSummary(
            @Parameter(description = "Tenant identifier", required = true)
            @RequestHeader("X-Tenant-ID") String tenantId) {

        log.debug("GET /api/v1/audit/summary tenantId={}", tenantId);
        AuditSummaryResponse summary = auditService.getAuditSummary(tenantId);
        return ResponseEntity.ok(ApiResponse.success(summary));
    }
}
