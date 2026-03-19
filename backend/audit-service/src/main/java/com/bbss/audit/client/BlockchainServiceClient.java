package com.bbss.audit.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.time.LocalDateTime;

/**
 * OpenFeign declarative HTTP client for the {@code blockchain-service}.
 *
 * <p>The service URL is resolved from the property
 * {@code services.blockchain-service.url}, which defaults to
 * {@code http://blockchain-service:8084} for container deployments and can be
 * overridden via the {@code BLOCKCHAIN_SERVICE_URL} environment variable.
 *
 * <p>Call sites should treat every method as potentially throwing a
 * {@code feign.FeignException} when the remote service is unavailable.  The
 * {@code BlockchainCommitService} wraps each call in a try/catch and
 * transitions the {@code AuditEntry} to {@link com.bbss.audit.domain.model.AuditStatus#FAILED}
 * on any connectivity error, leaving re-submission to the retry scheduler.
 */
@FeignClient(
    name  = "blockchain-service",
    url   = "${services.blockchain-service.url}"
)
public interface BlockchainServiceClient {

    /**
     * Submits an audit record to the blockchain ledger.
     *
     * @param request the audit payload to anchor
     * @return ledger coordinates (transaction ID and block number) on success
     */
    @PostMapping("/api/v1/blockchain/audit")
    BlockchainAuditResponse submitAudit(@RequestBody BlockchainAuditRequest request);

    /**
     * Retrieves the current ledger status of a previously submitted transaction.
     * Used by {@code getAuditEntry} to verify on-chain confirmation when the
     * local record has a {@code blockchainTxId} but the status is still PENDING.
     *
     * @param txId blockchain transaction hash
     * @return current transaction status and block information
     */
    @GetMapping("/api/v1/blockchain/transactions/{txId}")
    BlockchainTransactionResponse getTransaction(@PathVariable("txId") String txId);

    // ── Nested request / response records ─────────────────────────────────────

    /**
     * Payload sent to the blockchain-service when anchoring an audit record.
     *
     * @param auditId     business-level audit identifier for deduplication
     * @param tenantId    owning tenant
     * @param entityType  domain category (e.g. TRANSACTION, USER, TENANT)
     * @param entityId    identifier of the affected entity
     * @param action      event verb (e.g. TRANSACTION_VERIFIED, FRAUD_DETECTED)
     * @param actorId     identifier of the principal that triggered the event
     * @param payload     full JSON snapshot of the originating event
     * @param occurredAt  server-side instant at which the event occurred
     */
    record BlockchainAuditRequest(
            String        auditId,
            String        tenantId,
            String        entityType,
            String        entityId,
            String        action,
            String        actorId,
            String        payload,
            LocalDateTime occurredAt
    ) {}

    /**
     * Response returned by the blockchain-service after anchoring an audit record.
     *
     * @param auditId        echoed audit identifier for correlation
     * @param blockchainTxId immutable blockchain transaction hash
     * @param blockNumber    block number on the distributed ledger
     * @param success        {@code true} if the record was successfully committed
     */
    record BlockchainAuditResponse(
            String  auditId,
            String  blockchainTxId,
            String  blockNumber,
            boolean success
    ) {}

    /**
     * Response returned when querying the status of an existing blockchain transaction.
     *
     * @param transactionId  internal blockchain-service transaction identifier
     * @param blockchainTxId blockchain transaction hash
     * @param blockNumber    block number
     * @param status         current ledger status (e.g. CONFIRMED, PENDING)
     * @param timestamp      timestamp at which the transaction was mined
     */
    record BlockchainTransactionResponse(
            String        transactionId,
            String        blockchainTxId,
            String        blockNumber,
            String        status,
            LocalDateTime timestamp
    ) {}
}
