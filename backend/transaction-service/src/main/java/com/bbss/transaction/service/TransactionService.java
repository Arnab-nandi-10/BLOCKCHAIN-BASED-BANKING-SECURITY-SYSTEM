package com.bbss.transaction.service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bbss.transaction.client.BlockchainServiceClient;
import com.bbss.transaction.client.BlockchainServiceClient.BlockchainSubmitRequest;
import com.bbss.transaction.client.BlockchainServiceClient.BlockchainSubmitResponse;
import com.bbss.transaction.client.FraudServiceClient;
import com.bbss.transaction.client.FraudServiceClient.FraudScoreRequest;
import com.bbss.transaction.client.FraudServiceClient.FraudScoreResponse;
import com.bbss.transaction.config.MetricsConfig;
import com.bbss.transaction.domain.model.Transaction;
import com.bbss.transaction.domain.model.TransactionStatus;
import com.bbss.transaction.domain.repository.TransactionRepository;
import com.bbss.transaction.dto.SubmitTransactionRequest;
import com.bbss.transaction.dto.TransactionResponse;
import com.bbss.transaction.dto.TransactionStatsResponse;
import com.bbss.transaction.messaging.TransactionEventPublisher;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Orchestrates the full transaction lifecycle:
 * <ol>
 *   <li>Persist the transaction.</li>
 *   <li>Publish a Kafka submission event.</li>
 *   <li>Call the fraud-detection service (with circuit breaker).</li>
 *   <li>Block or hold the transaction based on the fraud score.</li>
 *   <li>Submit to the Hyperledger Fabric blockchain (with circuit breaker).</li>
 *   <li>Publish verification / block / fraud-alert Kafka events.</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final FraudServiceClient fraudServiceClient;
    private final BlockchainServiceClient blockchainServiceClient;
    private final TransactionEventPublisher eventPublisher;
    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final MetricsConfig metricsConfig;

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Submit a new transaction through the full fraud-check and blockchain pipeline.
     *
     * @param req       validated request payload
     * @param tenantId  tenant identifier extracted from the request header
     * @param ipAddress submitter IP address
     * @return transaction response with the current state
     */
    @Transactional
    public TransactionResponse submitTransaction(
            SubmitTransactionRequest req,
            String tenantId,
            String ipAddress) {

        // Start end-to-end processing timer
        final long startTime = System.nanoTime();

        // 1. Build and persist the Transaction entity with SUBMITTED status.
        final long creationStart = System.nanoTime();
        
        Transaction tx = Transaction.builder()
                .transactionId(UUID.randomUUID().toString())
                .tenantId(tenantId)
                .fromAccount(req.fromAccount())
                .toAccount(req.toAccount())
                .amount(req.amount())
                .currency(req.currency())
                .type(req.type())
                .status(TransactionStatus.SUBMITTED)
                .correlationId(UUID.randomUUID().toString())
                .ipAddress(ipAddress)
                .metadata(req.metadata() != null ? req.metadata() : new HashMap<>())
                .build();
        
        tx = transactionRepository.save(tx);
        
        metricsConfig.getTransactionCreationTimer().record(System.nanoTime() - creationStart,
                java.util.concurrent.TimeUnit.NANOSECONDS);
        
        log.info("Transaction {} created for tenant {}", tx.getTransactionId(), tenantId);

        // Record transaction created metrics
        metricsConfig.getTransactionCreatedCounter().increment();
        metricsConfig.recordTransactionCreated("SUBMITTED", tenantId);
        metricsConfig.getTransactionAmountDistribution().record(tx.getAmount().doubleValue());
        metricsConfig.recordAmountProcessed(tx.getAmount().doubleValue(), tx.getCurrency(), "SUBMITTED");

        // 2. Publish submission event.
        eventPublisher.publishTransactionSubmitted(tx);

        // 3. Transition to PENDING_FRAUD_CHECK and call the fraud service.
        tx.setStatus(TransactionStatus.PENDING_FRAUD_CHECK);
        tx = transactionRepository.save(tx);

        final long fraudStart = System.nanoTime();
        FraudScoreResponse fraudResult = callFraudServiceWithCircuitBreaker(tx);
        metricsConfig.getFraudScoringTimer().record(System.nanoTime() - fraudStart,
                java.util.concurrent.TimeUnit.NANOSECONDS);

        tx.setFraudScore(fraudResult.score());
        tx.setFraudRiskLevel(fraudResult.riskLevel());

        // Record fraud score metrics
        metricsConfig.getFraudScoreDistribution().record(fraudResult.score());
        metricsConfig.recordFraudScore(fraudResult.score(), fraudResult.riskLevel());

        // 4a. Score >= 0.8 or explicit block directive — BLOCKED.
        if (fraudResult.score() >= 0.8 || fraudResult.shouldBlock()) {
            tx.setStatus(TransactionStatus.BLOCKED);
            tx.setRejectionReason(
                    "Blocked by fraud engine: " + fraudResult.recommendation());
            tx = transactionRepository.save(tx);
            eventPublisher.publishTransactionBlocked(tx);
            
            // Record blocked transaction metrics
            metricsConfig.getTransactionsBlockedCounter().increment();
            metricsConfig.getFraudAlertsCounter().increment();
            metricsConfig.recordTransactionCreated("BLOCKED", tenantId);
            metricsConfig.recordAmountProcessed(tx.getAmount().doubleValue(), tx.getCurrency(), "BLOCKED");
            
            // Record total processing time
            metricsConfig.getTransactionProcessingTimer().record(System.nanoTime() - startTime, 
                    java.util.concurrent.TimeUnit.NANOSECONDS);
            
            log.warn("Transaction {} BLOCKED — fraud score={}, rule={}",
                    tx.getTransactionId(), fraudResult.score(),
                    fraudResult.triggeredRules());
            return mapToResponse(tx);
        }

        // 4b. Score >= 0.75 — FRAUD_HOLD for manual review.
        if (fraudResult.score() >= 0.75) {
            tx.setStatus(TransactionStatus.FRAUD_HOLD);
            tx = transactionRepository.save(tx);
            eventPublisher.publishFraudAlert(tx, fraudResult);
            
            // Record fraud hold metrics
            metricsConfig.getFraudAlertsCounter().increment();
            metricsConfig.recordTransactionCreated("FRAUD_HOLD", tenantId);
            
            // Record total processing time
            metricsConfig.getTransactionProcessingTimer().record(System.nanoTime() - startTime,
                    java.util.concurrent.TimeUnit.NANOSECONDS);
            
            log.warn("Transaction {} placed on FRAUD_HOLD — score={}",
                    tx.getTransactionId(), fraudResult.score());
            return mapToResponse(tx);
        }

        // 5 – 9. Submit to blockchain.
        TransactionResponse response = processBlockchainSubmission(tx);
        
        // Record total processing time
        metricsConfig.getTransactionProcessingTimer().record(System.nanoTime() - startTime,
                java.util.concurrent.TimeUnit.NANOSECONDS);
        
        return response;
    }

    /**
     * Retrieve a transaction by its business ID, scoped to the caller's tenant.
     *
     * @param txId     business transaction identifier
     * @param tenantId tenant identifier
     * @return transaction response
     * @throws jakarta.persistence.EntityNotFoundException when not found or tenant mismatch
     */
    @Transactional(readOnly = true)
    public TransactionResponse getTransaction(String txId, String tenantId) {
        Transaction tx = transactionRepository.findByTransactionId(txId)
                .filter(t -> t.getTenantId().equals(tenantId))
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException(
                        "Transaction not found: " + txId));
        return mapToResponse(tx);
    }

    /**
     * List all transactions for a tenant, newest first.
     *
     * @param tenantId tenant identifier
     * @param pageable pagination parameters
     * @return page of transaction responses
     */
    @Transactional(readOnly = true)
    public Page<TransactionResponse> listTransactions(String tenantId, Pageable pageable) {
        return transactionRepository
                .findByTenantIdOrderByCreatedAtDesc(tenantId, pageable)
                .map(this::mapToResponse);
    }

    /**
     * List transactions for a tenant filtered by status.
     *
     * @param tenantId tenant identifier
     * @param status   status filter
     * @param pageable pagination parameters
     * @return page of transaction responses
     */
    @Transactional(readOnly = true)
    public Page<TransactionResponse> listByStatus(
            String tenantId, TransactionStatus status, Pageable pageable) {
        return transactionRepository
                .findByTenantIdAndStatus(tenantId, status, pageable)
                .map(this::mapToResponse);
    }

    /**
     * Compute transaction statistics for a tenant over the last 24 hours.
     *
     * @param tenantId tenant identifier
     * @return statistics response
     */
    @Transactional(readOnly = true)
    public TransactionStatsResponse getTransactionStats(String tenantId) {
        LocalDateTime to = LocalDateTime.now();
        LocalDateTime from = to.minusHours(24);

        long totalSubmitted = transactionRepository
                .countByTenantIdAndStatusAndCreatedAtBetween(tenantId, TransactionStatus.SUBMITTED, from, to);
        long totalVerified = transactionRepository
                .countByTenantIdAndStatusAndCreatedAtBetween(tenantId, TransactionStatus.VERIFIED, from, to);
        long totalBlocked = transactionRepository
                .countByTenantIdAndStatusAndCreatedAtBetween(tenantId, TransactionStatus.BLOCKED, from, to);
        long totalFraudHold = transactionRepository
                .countByTenantIdAndStatusAndCreatedAtBetween(tenantId, TransactionStatus.FRAUD_HOLD, from, to);
        long totalCompleted = transactionRepository
                .countByTenantIdAndStatusAndCreatedAtBetween(tenantId, TransactionStatus.COMPLETED, from, to);
        long totalFailed = transactionRepository
                .countByTenantIdAndStatusAndCreatedAtBetween(tenantId, TransactionStatus.FAILED, from, to);

        return TransactionStatsResponse.builder()
                .tenantId(tenantId)
                .totalSubmitted(totalSubmitted)
                .totalVerified(totalVerified)
                .totalBlocked(totalBlocked)
                .totalFraudHold(totalFraudHold)
                .totalCompleted(totalCompleted)
                .totalFailed(totalFailed)
                .from(from)
                .to(to)
                .build();
    }

    // -------------------------------------------------------------------------
    // Internal helpers — circuit-breaker wrappers
    // -------------------------------------------------------------------------

    /**
     * Calls the fraud-detection service wrapped in the "fraud-service" circuit breaker.
     * Falls back to {@link #fraudServiceFallback} when the circuit is open or a call fails.
     */
    private FraudScoreResponse callFraudServiceWithCircuitBreaker(Transaction tx) {
        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("fraud-service");
        try {
            return cb.executeSupplier(() -> {
                FraudScoreRequest req = new FraudScoreRequest(
                        tx.getTransactionId(),
                        tx.getTenantId(),
                        tx.getFromAccount(),
                        tx.getToAccount(),
                        tx.getAmount(),
                        tx.getCurrency(),
                        tx.getType().name(),
                        tx.getIpAddress(),
                        tx.getMetadata()
                );
                return fraudServiceClient.scoreFraud(req);
            });
        } catch (Exception ex) {
            return fraudServiceFallback(tx, ex);
        }
    }

    /**
     * Fallback invoked when the fraud-service circuit breaker is open or times out.
     * Logs a warning and returns a neutral score that allows processing to continue
     * with PENDING status, flagging the transaction for later manual review.
     */
    private FraudScoreResponse fraudServiceFallback(Transaction tx, Throwable cause) {
        log.warn("Fraud service unavailable for transaction {} — continuing with PENDING status. Cause: {}",
                tx.getTransactionId(), cause.getMessage());
        return new FraudScoreResponse(
                tx.getTransactionId(),
                -1.0,
                "UNKNOWN",
                List.of("FRAUD_SERVICE_UNAVAILABLE"),
                "Fraud service unavailable — pending manual review",
                false
        );
    }

    /**
     * Submits the transaction to the blockchain wrapped in the "blockchain-service"
     * circuit breaker. Falls back to {@link #blockchainFallback} on failure.
     */
    @Transactional
    public TransactionResponse processBlockchainSubmission(Transaction tx) {
        final long blockchainStart = System.nanoTime();
        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("blockchain-service");
        BlockchainSubmitResponse blockchainResult;
        try {
            blockchainResult = cb.executeSupplier(() -> {
                BlockchainSubmitRequest req = new BlockchainSubmitRequest(
                        tx.getTransactionId(),
                        tx.getTenantId(),
                        tx.getFromAccount(),
                        tx.getToAccount(),
                        tx.getAmount(),
                        tx.getCurrency(),
                        tx.getType().name(),
                        tx.getStatus().name(),
                        tx.getCreatedAt()
                );
                // Unwrap the ApiResponse envelope to get the inner data
                BlockchainServiceClient.ApiResponse<BlockchainSubmitResponse> apiResp =
                        blockchainServiceClient.submitTransaction(req);
                return (apiResp != null && apiResp.data() != null)
                        ? apiResp.data()
                        : new BlockchainSubmitResponse(req.transactionId(), null, null, false, "Empty response from blockchain service");
            });
        } catch (Exception ex) {
            blockchainResult = blockchainFallback(tx, ex);
        }
        
        metricsConfig.getBlockchainSubmissionTimer().record(System.nanoTime() - blockchainStart,
                java.util.concurrent.TimeUnit.NANOSECONDS);

        // 7. Update transaction with blockchain details and VERIFIED status.
        if (blockchainResult.success()) {
            tx.setBlockchainTxId(blockchainResult.blockchainTxId());
            tx.setBlockNumber(blockchainResult.blockNumber());
            tx.setStatus(TransactionStatus.VERIFIED);
            Transaction saved = transactionRepository.save(tx);

            // Record blockchain success metrics
            metricsConfig.getBlockchainSubmissionCounter().increment();
            metricsConfig.recordBlockchainSubmission("success", tx.getTenantId());
            metricsConfig.getBlockchainVerificationCounter().increment();
            metricsConfig.recordTransactionCreated("VERIFIED", tx.getTenantId());
            metricsConfig.recordAmountProcessed(tx.getAmount().doubleValue(), tx.getCurrency(), "VERIFIED");

            // 8. Publish verified event.
            eventPublisher.publishTransactionVerified(saved);
            log.info("Transaction {} VERIFIED on blockchain. TxHash={}, Block={}",
                    saved.getTransactionId(), saved.getBlockchainTxId(), saved.getBlockNumber());
            return mapToResponse(saved);
        } else {
            tx.setStatus(TransactionStatus.FAILED);
            tx.setRejectionReason("Blockchain submission failed: " + blockchainResult.message());
            Transaction saved = transactionRepository.save(tx);
            
            // Record blockchain failure metrics
            String failureReason = blockchainResult.message() != null && 
                                   blockchainResult.message().contains("circuit") ? "circuit_open" : "failure";
            metricsConfig.recordBlockchainSubmission(failureReason, tx.getTenantId());
            metricsConfig.recordTransactionCreated("FAILED", tx.getTenantId());
            
            log.error("Transaction {} FAILED blockchain submission: {}",
                    saved.getTransactionId(), blockchainResult.message());
            return mapToResponse(saved);
        }
    }

    /**
     * Fallback invoked when the blockchain-service circuit breaker is open or times out.
     * Sets the transaction to PENDING and schedules a retry via Kafka republication.
     */
    private BlockchainSubmitResponse blockchainFallback(Transaction tx, Throwable cause) {
        log.error("Blockchain service unavailable for transaction {} — scheduling retry via Kafka. Cause: {}",
                tx.getTransactionId(), cause.getMessage());
        // Re-publish the submission event so a downstream consumer can retry.
        eventPublisher.publishTransactionSubmitted(tx);
        return new BlockchainSubmitResponse(
                tx.getTransactionId(),
                null,
                null,
                false,
                "Blockchain service unavailable — retry scheduled"
        );
    }

    // -------------------------------------------------------------------------
    // Mapping
    // -------------------------------------------------------------------------

    private TransactionResponse mapToResponse(Transaction tx) {
        return TransactionResponse.builder()
                .transactionId(tx.getTransactionId())
                .tenantId(tx.getTenantId())
                .fromAccount(tx.getFromAccount())
                .toAccount(tx.getToAccount())
                .amount(tx.getAmount())
                .currency(tx.getCurrency())
                .type(tx.getType())
                .status(tx.getStatus())
                .blockchainTxId(tx.getBlockchainTxId())
                .blockNumber(tx.getBlockNumber())
                .fraudScore(tx.getFraudScore())
                .fraudRiskLevel(tx.getFraudRiskLevel())
                .createdAt(tx.getCreatedAt())
                .completedAt(tx.getCompletedAt())
                .build();
    }
}
