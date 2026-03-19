package com.bbss.audit.service;

import org.springframework.stereotype.Service;

import com.bbss.audit.client.BlockchainServiceClient;

import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Adapter service that wraps {@link BlockchainServiceClient} Feign calls with circuit breaker protection.
 *
 * <p>This separation allows us to apply Resilience4j annotations without coupling them
 * directly to the Feign interface definition. Circuit breaker annotations on service
 * methods, not Feign interfaces, provide cleaner separation of concerns.</p>
 *
 * <p><strong>Circuit Breaker Configuration (blockchainClient):</strong></p>
 * <ul>
 *   <li><strong>Failure Threshold</strong>: 50% (moderate tolerance for audit operations)</li>
 *   <li><strong>Open Duration</strong>: 30s (blockchain recovery takes time)</li>
 *   <li><strong>Slow Call Threshold</strong>: 25s (REST call to blockchain-service + Fabric operations)</li>
 *   <li><strong>Bulkhead</strong>: Max 10 concurrent calls (REST has higher concurrency than gRPC)</li>
 * </ul>
 *
 * <p><strong>Graceful Degradation:</strong> When the circuit is OPEN, fallback methods return
 * {@code null} to signal unavailability. Callers (e.g. {@link BlockchainCommitService}) should
 * handle {@code null} responses by transitioning audit entries to {@code FAILED} status for
 * eventual retry.</p>
 *
 * @see BlockchainServiceClient
 * @see BlockchainCommitService
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BlockchainClientAdapter {

    private final BlockchainServiceClient blockchainServiceClient;

    /**
     * Submit an audit record to the blockchain ledger with circuit breaker protection.
     *
     * <p>If blockchain-service is unavailable or the circuit is OPEN, returns {@code null}
     * to signal failure. The caller should handle this by marking the audit entry as FAILED.</p>
     *
     * @param request the audit payload to anchor
     * @return ledger coordinates on success, {@code null} on circuit open or failure
     */
    @CircuitBreaker(name = "blockchainClient", fallbackMethod = "submitAuditFallback")
    @Bulkhead(name = "blockchainClient")
    public BlockchainServiceClient.BlockchainAuditResponse submitAudit(
            BlockchainServiceClient.BlockchainAuditRequest request) {
        
        log.debug("Calling blockchain-service.submitAudit for auditId={}", request.auditId());
        return blockchainServiceClient.submitAudit(request);
    }

    /**
     * Fallback for {@link #submitAudit} when circuit breaker is OPEN or bulkhead is FULL.
     *
     * @param request original request (for logging)
     * @param throwable exception that triggered fallback
     * @return {@code null} to signal unavailability
     */
    private BlockchainServiceClient.BlockchainAuditResponse submitAuditFallback(
            BlockchainServiceClient.BlockchainAuditRequest request, Throwable throwable) {
        
        log.warn("Circuit breaker OPEN or bulkhead FULL for submitAudit: auditId={} reason={}",
                request.auditId(), throwable.getMessage());
        
        // Return null to signal failure to BlockchainCommitService
        // Caller will mark audit entry as FAILED for eventual retry
        return null;
    }

    /**
     * Retrieve the status of a previously submitted blockchain transaction with circuit breaker protection.
     *
     * @param txId blockchain transaction hash
     * @return transaction status on success, {@code null} on circuit open or failure
     */
    @CircuitBreaker(name = "blockchainClient", fallbackMethod = "getTransactionFallback")
    @Bulkhead(name = "blockchainClient")
    public BlockchainServiceClient.BlockchainTransactionResponse getTransaction(String txId) {
        log.debug("Calling blockchain-service.getTransaction for txId={}", txId);
        return blockchainServiceClient.getTransaction(txId);
    }

    /**
     * Fallback for {@link #getTransaction} when circuit breaker is OPEN or bulkhead is FULL.
     *
     * @param txId original transaction ID (for logging)
     * @param throwable exception that triggered fallback
     * @return {@code null} to signal unavailability
     */
    private BlockchainServiceClient.BlockchainTransactionResponse getTransactionFallback(
            String txId, Throwable throwable) {
        
        log.warn("Circuit breaker OPEN or bulkhead FULL for getTransaction: txId={} reason={}",
                txId, throwable.getMessage());
        
        // Return null to signal data unavailable
        return null;
    }
}
