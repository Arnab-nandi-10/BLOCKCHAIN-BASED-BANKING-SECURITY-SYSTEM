package com.bbss.audit.service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bbss.audit.client.BlockchainServiceClient;
import com.bbss.audit.domain.model.AuditEntry;
import com.bbss.audit.domain.model.AuditStatus;
import com.bbss.audit.domain.repository.AuditRepository;
import com.bbss.audit.dto.AuditResponse;
import com.bbss.audit.dto.AuditSummaryResponse;
import com.bbss.shared.events.AuditEvent;
import com.bbss.shared.exception.ResourceNotFoundException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Core business logic for the audit trail.
 *
 * <p>Responsibilities:
 * <ol>
 *   <li>Idempotent ingestion of {@link AuditEvent} objects into the local
 *       PostgreSQL database (status = PENDING).</li>
 *   <li>Delegating asynchronous blockchain anchoring to
 *       {@link BlockchainCommitService} (avoids Spring AOP self-invocation
 *       pitfall).</li>
 *   <li>Querying audit entries with tenant-scoped isolation.</li>
 *   <li>Scheduled retry of PENDING/FAILED records.</li>
 * </ol>
 *
 * <p><strong>Circuit Breaker Protection:</strong> All blockchain-service calls are
 * routed through {@link BlockchainClientAdapter}, which applies Resilience4j fault
 * tolerance patterns.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuditService {

    private final AuditRepository          auditRepository;
    private final BlockchainCommitService   blockchainCommitService;
    private final BlockchainClientAdapter   blockchainClientAdapter;
    private final ObjectMapper              objectMapper;

    // ── Ingestion ─────────────────────────────────────────────────────────────

    /**
     * Records an auditable event.
     *
     * <p>Processing steps:
     * <ol>
     *   <li>Idempotency check – if an entry with the same {@code eventId} already
     *       exists, the existing record is returned immediately.</li>
     *   <li>Build and persist a new {@link AuditEntry} with
     *       {@link AuditStatus#PENDING}.</li>
     *   <li>Asynchronously submit the entry to the blockchain ledger via
     *       {@link BlockchainCommitService#commitToBlockchainAsync}.  If the
     *       blockchain-service is unavailable the entry remains PENDING and will
     *       be retried by the scheduler.</li>
     * </ol>
     *
     * @param event the shared-library event produced by any BBSS microservice
     * @return the persisted {@link AuditEntry} (status may still be PENDING at
     *         the time of return)
     */
    @Transactional
    public AuditEntry createAuditEntry(AuditEvent event) {
        // 1. Idempotency check ────────────────────────────────────────────────
        Optional<AuditEntry> existing = auditRepository.findByAuditId(event.getEventId());
        if (existing.isPresent()) {
            log.debug("Duplicate audit event ignored: auditId={}", event.getEventId());
            return existing.get();
        }

        // 2. Serialize payload to JSON string ─────────────────────────────────
        String payloadJson = serializePayload(event.getPayload());

        // 3. Build and save with PENDING status ───────────────────────────────
        AuditEntry entry = AuditEntry.builder()
                .auditId(event.getEventId())
                .tenantId(event.getTenantId())
                .entityType(event.getEntityType())
                .entityId(event.getEntityId())
                .action(event.getAction())
                .actorId(event.getActorId() != null ? event.getActorId() : "SYSTEM")
                .actorType(event.getActorType() != null ? event.getActorType() : "SYSTEM")
                .ipAddress(event.getIpAddress())
                .payload(payloadJson)
                .status(AuditStatus.PENDING)
                .correlationId(event.getCorrelationId())
                .build();

        AuditEntry saved = auditRepository.save(entry);
        log.info("Audit entry persisted: auditId={} tenantId={} action={}",
                saved.getAuditId(), saved.getTenantId(), saved.getAction());

        // 4. Async blockchain commit (fire-and-forget; retried on failure) ────
        blockchainCommitService.commitToBlockchainAsync(saved.getId());

        return saved;
    }

    // ── Queries ───────────────────────────────────────────────────────────────

    /**
     * Returns a single audit entry by its business ID, scoped to the given tenant.
     *
     * <p>If the entry is COMMITTED and has a {@code blockchainTxId}, the
     * blockchain-service is queried to verify the on-chain block number.  Any
     * connectivity failure is silently logged; the local record is always returned.
     *
     * @param auditId  business-level audit identifier
     * @param tenantId the requesting tenant (enforces isolation)
     * @return the matching {@link AuditResponse}
     * @throws ResourceNotFoundException if no entry exists for the given auditId
     *         and tenantId combination
     */
    @Transactional(readOnly = true)
    public AuditResponse getAuditEntry(String auditId, String tenantId) {
        AuditEntry entry = auditRepository.findByAuditId(auditId)
                .filter(e -> e.getTenantId().equals(tenantId))
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Audit entry not found: auditId=" + auditId + " tenantId=" + tenantId));

        // Optionally verify on-chain block number if the entry is COMMITTED
        if (entry.getStatus() == AuditStatus.COMMITTED
                && entry.getBlockchainTxId() != null
                && !entry.getBlockchainTxId().isBlank()) {
            refreshBlockchainDetails(entry);
        }

        return toAuditResponse(entry);
    }

    /**
     * Returns a paginated list of all audit entries for a tenant, most recent first.
     */
    @Transactional(readOnly = true)
    public Page<AuditResponse> listAuditEntries(String tenantId, Pageable pageable) {
        return auditRepository
                .findByTenantIdOrderByOccurredAtDesc(tenantId, pageable)
                .map(this::toAuditResponse);
    }

    /**
     * Returns a paginated list of audit entries for a specific entity instance.
     */
    @Transactional(readOnly = true)
    public Page<AuditResponse> listByEntityId(
            String tenantId,
            String entityType,
            String entityId,
            Pageable pageable) {
        return auditRepository
                .findByTenantIdAndEntityTypeAndEntityId(tenantId, entityType, entityId, pageable)
                .map(this::toAuditResponse);
    }

    /**
     * Returns a paginated list of audit entries for a specific action verb.
     */
    @Transactional(readOnly = true)
    public Page<AuditResponse> listByAction(String tenantId, String action, Pageable pageable) {
        return auditRepository
                .findByTenantIdAndAction(tenantId, action, pageable)
                .map(this::toAuditResponse);
    }

    /**
     * Returns a paginated list of audit entries that occurred within the given
     * time window (inclusive).
     */
    @Transactional(readOnly = true)
    public Page<AuditResponse> listByDateRange(
            String tenantId,
            LocalDateTime from,
            LocalDateTime to,
            Pageable pageable) {
        return auditRepository
                .findByTenantIdAndOccurredAtBetween(tenantId, from, to, pageable)
                .map(this::toAuditResponse);
    }

    /**
     * Builds an audit summary for a tenant: total counts, last-24-h activity,
     * pending/failed counts, and a per-action breakdown.
     */
    @Transactional(readOnly = true)
    public AuditSummaryResponse getAuditSummary(String tenantId) {
        LocalDateTime now       = LocalDateTime.now();
        LocalDateTime yesterday = now.minusHours(24);

        long totalEntries   = auditRepository.countByTenantId(tenantId);
        long last24hEntries = auditRepository.countByTenantIdAndOccurredAtBetween(tenantId, yesterday, now);
        long pendingEntries = auditRepository.countByTenantIdAndStatus(tenantId, AuditStatus.PENDING);
        long failedEntries  = auditRepository.countByTenantIdAndStatus(tenantId, AuditStatus.FAILED);

        List<Object[]> rawBreakdown = auditRepository.findActionBreakdownByTenantId(tenantId);
        Map<String, Long> actionBreakdown = rawBreakdown.stream()
                .collect(Collectors.toMap(
                        row -> (String) row[0],
                        row -> (Long)   row[1],
                        (a, b) -> a,
                        LinkedHashMap::new
                ));

        return AuditSummaryResponse.builder()
                .tenantId(tenantId)
                .totalEntries(totalEntries)
                .last24hEntries(last24hEntries)
                .pendingEntries(pendingEntries)
                .failedEntries(failedEntries)
                .actionBreakdown(actionBreakdown)
                .generatedAt(now)
                .build();
    }

    // ── Scheduled retry ───────────────────────────────────────────────────────

    /**
     * Periodically re-submits PENDING and FAILED audit entries to the blockchain.
     *
     * <p>Runs every 60 seconds ({@code fixedDelay=60000} ms after the previous
     * execution completes, not on a fixed rate).  Each eligible entry is
     * dispatched to the async thread pool; the thread pool queue capacity
     * (100 slots) acts as a natural back-pressure valve.
     */
    @Scheduled(fixedDelay = 60_000L)
    public void retryFailedEntries() {
        List<AuditEntry> candidates =
                auditRepository.findByStatusIn(List.of(AuditStatus.PENDING, AuditStatus.FAILED));

        if (candidates.isEmpty()) {
            return;
        }

        log.info("Retry scheduler: submitting {} PENDING/FAILED audit entries to blockchain",
                candidates.size());

        for (AuditEntry entry : candidates) {
            blockchainCommitService.commitToBlockchainAsync(entry.getId());
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private String serializePayload(Object payload) {
        if (payload == null) {
            return "{}";
        }
        if (payload instanceof String s) {
            return s;
        }
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize audit payload: {}", e.getMessage());
            return "{}";
        }
    }

    /**
     * Queries the blockchain-service to refresh the block number on a COMMITTED
     * entry.  Any error is silently absorbed.
     */
    private void refreshBlockchainDetails(AuditEntry entry) {
        try {
            // Call blockchain-service through circuit-breaker-protected adapter
            BlockchainServiceClient.BlockchainTransactionResponse txResp =
                    blockchainClientAdapter.getTransaction(entry.getBlockchainTxId());

            if (txResp != null
                    && txResp.blockNumber() != null
                    && !txResp.blockNumber().equals(entry.getBlockNumber())) {
                entry.setBlockNumber(txResp.blockNumber());
                auditRepository.save(entry);
            }
        } catch (FeignException e) {
            log.debug("Could not refresh blockchain details for auditId={}: HTTP {}",
                    entry.getAuditId(), e.status());
        } catch (Exception e) {
            log.debug("Could not refresh blockchain details for auditId={}: {}",
                    entry.getAuditId(), e.getMessage());
        }
    }

    /**
     * Maps an {@link AuditEntry} entity to a REST-safe {@link AuditResponse} DTO.
     */
    public AuditResponse toAuditResponse(AuditEntry entry) {
        return AuditResponse.builder()
                .auditId(entry.getAuditId())
                .tenantId(entry.getTenantId())
                .entityType(entry.getEntityType())
                .entityId(entry.getEntityId())
                .action(entry.getAction())
                .actorId(entry.getActorId())
                .actorType(entry.getActorType())
                .ipAddress(entry.getIpAddress())
                .payload(entry.getPayload())
                .blockchainTxId(entry.getBlockchainTxId())
                .blockNumber(entry.getBlockNumber())
                .status(entry.getStatus())
                .correlationId(entry.getCorrelationId())
                .occurredAt(entry.getOccurredAt())
                .build();
    }
}
