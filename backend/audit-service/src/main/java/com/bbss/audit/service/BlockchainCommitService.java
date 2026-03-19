package com.bbss.audit.service;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.bbss.audit.client.BlockchainServiceClient;
import com.bbss.audit.domain.model.AuditEntry;
import com.bbss.audit.domain.model.AuditStatus;
import com.bbss.audit.domain.repository.AuditRepository;

import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Handles the asynchronous blockchain anchoring of a single {@link AuditEntry}.
 *
 * <p>This class exists as a <em>separate Spring bean</em> specifically so that
 * the {@link Async} proxy is applied correctly.  If the async method were
 * defined on {@link AuditService} and called from the same instance, Spring's
 * AOP proxy would be bypassed and the method would execute synchronously on
 * the calling thread.
 *
 * <p>Thread pool: {@code auditBlockchainExecutor} (configured in
 * {@link AsyncConfig}).
 *
 * <p><strong>Circuit Breaker Protection:</strong> All blockchain-service calls are
 * routed through {@link BlockchainClientAdapter}, which applies Resilience4j circuit
 * breaker and bulkhead patterns for fault tolerance.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BlockchainCommitService {

    private final AuditRepository        auditRepository;
    private final BlockchainClientAdapter blockchainClientAdapter;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    private static final String AUDIT_EVENTS_TOPIC = "audit.entry";

    /**
     * Commits the audit entry identified by {@code entryId} to the blockchain
     * ledger and updates the local database record with the resulting ledger
     * coordinates.
     *
     * <p>On success the entry transitions to {@link AuditStatus#COMMITTED} and
     * an event is published to the {@code audit.entry} Kafka topic.
     *
     * <p>On any failure (network error, non-success response) the entry
     * transitions to {@link AuditStatus#FAILED}, leaving it eligible for
     * re-processing by the retry scheduler in {@link AuditService}.
     *
     * @param entryId surrogate key of the {@link AuditEntry} to commit
     */
    @Async("auditBlockchainExecutor")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void commitToBlockchainAsync(UUID entryId) {
        AuditEntry entry = auditRepository.findById(entryId).orElse(null);
        if (entry == null) {
            log.warn("BlockchainCommitService: AuditEntry not found for id={}", entryId);
            return;
        }

        log.debug("Submitting audit entry auditId={} to blockchain", entry.getAuditId());

        try {
            BlockchainServiceClient.BlockchainAuditRequest request =
                    new BlockchainServiceClient.BlockchainAuditRequest(
                            entry.getAuditId(),
                            entry.getTenantId(),
                            entry.getEntityType(),
                            entry.getEntityId(),
                            entry.getAction(),
                            entry.getActorId(),
                            entry.getPayload(),
                            entry.getOccurredAt()
                    );

            // Call blockchain-service through circuit-breaker-protected adapter
            BlockchainServiceClient.BlockchainAuditResponse response =
                    blockchainClientAdapter.submitAudit(request);

            if (response != null && response.success()) {
                entry.setBlockchainTxId(response.blockchainTxId());
                entry.setBlockNumber(response.blockNumber());
                entry.setStatus(AuditStatus.COMMITTED);
                auditRepository.save(entry);

                publishAuditEvent(entry);

                log.info("Audit entry auditId={} committed to blockchain txId={} block={}",
                        entry.getAuditId(), response.blockchainTxId(), response.blockNumber());

            } else {
                markFailed(entry, "blockchain-service returned success=false");
            }

        } catch (FeignException e) {
            markFailed(entry,
                    "blockchain-service unavailable (HTTP " + e.status() + "): " + e.getMessage());
        } catch (Exception e) {
            markFailed(entry, "unexpected error: " + e.getMessage());
            log.error("Unexpected error committing audit entry auditId={} to blockchain",
                    entry.getAuditId(), e);
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private void markFailed(AuditEntry entry, String reason) {
        log.warn("Failed to commit audit entry auditId={} – {}",
                entry.getAuditId(), reason);
        entry.setStatus(AuditStatus.FAILED);
        auditRepository.save(entry);
    }

    /**
     * Publishes a lightweight notification to the {@code audit.entry} topic so
     * that downstream consumers (e.g. notification-service, dashboards) are
     * aware that the record is now anchored on the ledger.
     */
    private void publishAuditEvent(AuditEntry entry) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("auditId",         entry.getAuditId());
            payload.put("tenantId",        entry.getTenantId());
            payload.put("entityType",      entry.getEntityType());
            payload.put("entityId",        entry.getEntityId());
            payload.put("action",          entry.getAction());
            payload.put("actorId",         entry.getActorId());
            payload.put("blockchainTxId",  entry.getBlockchainTxId() != null ? entry.getBlockchainTxId() : "");
            payload.put("blockNumber",     entry.getBlockNumber()    != null ? entry.getBlockNumber()    : "");
            payload.put("status",          entry.getStatus().name());
            payload.put("occurredAt",      entry.getOccurredAt() != null ? entry.getOccurredAt().toString() : "");
            payload.put("correlationId",   entry.getCorrelationId() != null ? entry.getCorrelationId() : "");

            kafkaTemplate.send(AUDIT_EVENTS_TOPIC, entry.getTenantId(), payload);
            log.debug("Published audit.entry event for auditId={}", entry.getAuditId());

        } catch (Exception e) {
            log.warn("Failed to publish audit.entry Kafka event for auditId={}: {}",
                    entry.getAuditId(), e.getMessage());
        }
    }
}
