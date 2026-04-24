package com.bbss.transaction.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bbss.transaction.client.FraudServiceClient;
import com.bbss.transaction.client.FraudServiceClient.FraudScoreRequest;
import com.bbss.transaction.client.FraudServiceClient.FraudScoreResponse;
import com.bbss.transaction.client.TenantServiceClient;
import com.bbss.transaction.config.MetricsConfig;
import com.bbss.transaction.domain.model.LedgerStatus;
import com.bbss.transaction.domain.model.Transaction;
import com.bbss.transaction.domain.model.TransactionStatus;
import com.bbss.transaction.domain.model.TransactionType;
import com.bbss.transaction.domain.model.VerificationStatus;
import com.bbss.transaction.domain.repository.TransactionRepository;
import com.bbss.transaction.dto.SubmitTransactionRequest;
import com.bbss.transaction.dto.TransactionQueryFilters;
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
 *   <li>Persist the post-fraud decision locally.</li>
 *   <li>Queue an asynchronous Fabric anchoring request via Kafka.</li>
 *   <li>Update ledger coordinates later when blockchain-service publishes block.committed.</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionService {

    private static final List<String> TENANT_FRAUD_CONFIG_KEYS = List.of(
            "fraud.threshold.block",
            "fraud.threshold.hold",
            "fraud.maxTransactionAmount",
            "fraud.unusualHourStart",
            "fraud.unusualHourEnd",
            "fraud.highRiskCurrencies",
            "fraud.highRiskCountries",
            "blockchain.mode",
            "blockchain.requireRealFabric",
            "blockchain.fallbackAllowed"
    );

    private final TransactionRepository transactionRepository;
    private final FraudServiceClient fraudServiceClient;
    private final TenantServiceClient tenantServiceClient;
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
    @SuppressWarnings("null")
    public TransactionResponse submitTransaction(
            SubmitTransactionRequest req,
            String tenantId,
            String ipAddress,
            String requestId) {

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
                .ledgerStatus(LedgerStatus.NOT_REQUESTED)
                .verificationStatus(VerificationStatus.NOT_VERIFIED)
                .correlationId(resolveCorrelationId(requestId))
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
        tx.setFraudDecision(fraudResult.decision());
        tx.setFraudRecommendation(fraudResult.recommendation());
        tx.setReviewRequired(fraudResult.reviewRequired());
        tx.setTriggeredRules(new ArrayList<>(fraudResult.triggeredRules()));
        tx.setExplanations(new ArrayList<>(fraudResult.explanations()));

        // Record fraud score metrics
        metricsConfig.getFraudScoreDistribution().record(fraudResult.score());
        metricsConfig.recordFraudScore(fraudResult.score(), fraudResult.riskLevel());

        // 4a. Explicit block directive from the hybrid fraud engine.
        if ("BLOCK".equalsIgnoreCase(fraudResult.decision()) || fraudResult.shouldBlock()) {
            tx.setStatus(TransactionStatus.BLOCKED);
            tx.setRejectionReason(
                    "Blocked by fraud engine: " + fraudResult.recommendation());
            tx = queueLedgerCommitRequest(tx);
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

        // 4b. High risk or degraded-mode review requirement — FRAUD_HOLD for manual review.
        if ("HOLD".equalsIgnoreCase(fraudResult.decision()) || fraudResult.reviewRequired()) {
            tx.setStatus(TransactionStatus.FRAUD_HOLD);
            tx.setRejectionReason("Held for fraud review: " + fraudResult.recommendation());
            tx = queueLedgerCommitRequest(tx);
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

        tx.setStatus(TransactionStatus.VERIFIED);
        tx.setRejectionReason(null);
        tx = queueLedgerCommitRequest(tx);
        TransactionResponse response = mapToResponse(tx);
        
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
    public Page<TransactionResponse> listTransactions(
            String tenantId,
            TransactionQueryFilters filters,
            Pageable pageable) {
        return transactionRepository
            .findAll(buildSpecification(tenantId, filters), java.util.Objects.requireNonNull(pageable, "pageable must not be null"))
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
        return listTransactions(
                tenantId,
                new TransactionQueryFilters(null, status, null, null, null, null, null),
                pageable
        );
    }

    /**
     * Compute transaction statistics for a tenant over the last 24 hours.
     *
     * @param tenantId tenant identifier
     * @return statistics response
     */
    @Transactional(readOnly = true)
    public TransactionStatsResponse getTransactionStats(
            String tenantId,
            LocalDateTime from,
            LocalDateTime to) {
        LocalDateTime windowTo = to != null ? to : LocalDateTime.now();
        LocalDateTime windowFrom = from != null ? from : windowTo.minusDays(6).toLocalDate().atStartOfDay();

        List<Transaction> allTransactions = transactionRepository.findAll(byTenant(tenantId));
        List<Transaction> windowTransactions = allTransactions.stream()
                .filter(tx -> tx.getCreatedAt() != null)
                .filter(tx -> !tx.getCreatedAt().isBefore(windowFrom) && !tx.getCreatedAt().isAfter(windowTo))
                .sorted(Comparator.comparing(Transaction::getCreatedAt))
                .toList();

        Map<String, Long> statusCounts = countStatuses(
                allTransactions,
                Transaction::getStatus,
                TransactionStatus.values()
        );
        Map<String, Long> ledgerStatusCounts = countStatuses(
                allTransactions,
                Transaction::getLedgerStatus,
                LedgerStatus.values()
        );
        Map<String, Long> verificationStatusCounts = countStatuses(
                allTransactions,
                Transaction::getVerificationStatus,
                VerificationStatus.values()
        );
        Map<String, Long> fraudRiskDistribution = countFraudRiskLevels(allTransactions);

        return TransactionStatsResponse.builder()
                .tenantId(tenantId)
                .totalTransactions(allTransactions.size())
                .totalAmount(sumAmounts(allTransactions))
                .totalSubmitted(statusCounts.getOrDefault(TransactionStatus.SUBMITTED.name(), 0L))
                .totalVerified(statusCounts.getOrDefault(TransactionStatus.VERIFIED.name(), 0L))
                .totalBlocked(statusCounts.getOrDefault(TransactionStatus.BLOCKED.name(), 0L))
                .totalFraudHold(statusCounts.getOrDefault(TransactionStatus.FRAUD_HOLD.name(), 0L))
                .totalCompleted(statusCounts.getOrDefault(TransactionStatus.COMPLETED.name(), 0L))
                .totalFailed(statusCounts.getOrDefault(TransactionStatus.FAILED.name(), 0L))
                .statusCounts(statusCounts)
                .ledgerStatusCounts(ledgerStatusCounts)
                .verificationStatusCounts(verificationStatusCounts)
                .fraudRiskDistribution(fraudRiskDistribution)
                .dailyVolume(buildDailyVolume(windowFrom, windowTo, windowTransactions))
                .windowTransactions(windowTransactions.size())
                .windowAmount(sumAmounts(windowTransactions))
                .from(windowFrom)
                .to(windowTo)
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
                        tx.getCreatedAt() != null ? tx.getCreatedAt().toString() : null,
                        tx.getIpAddress(),
                        resolveFraudMetadata(tx)
                );
                return fraudServiceClient.scoreFraud(req);
            });
        } catch (Exception ex) {
            return fraudServiceFallback(tx, ex);
        }
    }

    /**
     * Fallback invoked when the fraud-service circuit breaker is open or times out.
     * Logs a warning and returns a neutral score that routes the transaction
     * to manual review.
     */
    private FraudScoreResponse fraudServiceFallback(Transaction tx, Throwable cause) {
        log.warn("Fraud service unavailable for transaction {} — routing to manual review. Cause: {}",
                tx.getTransactionId(), cause.getMessage());
        return new FraudScoreResponse(
                tx.getTransactionId(),
                0.50,
                0.50,
                0.0,
                0.0,
                "MEDIUM",
                "HOLD",
                List.of("FRAUD_SERVICE_UNAVAILABLE"),
                List.of("Fraud scoring service was unavailable, so a neutral score was assigned and the transaction was sent to manual review."),
                "MANUAL_REVIEW",
                true,
                false,
                true,
                "unavailable",
                0.0
        );
    }

    /**
     * Marks the transaction as pending Fabric anchoring and publishes a Kafka
     * request for blockchain-service to handle asynchronously.
     */
    @Transactional
    public Transaction queueLedgerCommitRequest(Transaction tx) {
        tx.setLedgerStatus(LedgerStatus.PENDING_LEDGER);
        tx.setVerificationStatus(VerificationStatus.NOT_VERIFIED);
        Transaction saved = transactionRepository.save(tx);

        metricsConfig.recordBlockchainSubmission("queued", saved.getTenantId());
        eventPublisher.publishLedgerCommitRequested(saved);

        log.info("Transaction {} queued for asynchronous blockchain anchoring with status={}",
                saved.getTransactionId(), saved.getStatus());
        return saved;
    }

    /**
     * Called by the block.committed consumer once blockchain-service has a real
     * Fabric tx ID and block number.
     */
    @Transactional
    public void markLedgerCommitted(
            String transactionId,
            String blockchainTxId,
            String blockNumber,
            String verificationStatus,
            String payloadHash,
            String recordHash,
            String previousHash) {

        transactionRepository.findByTransactionId(transactionId).ifPresentOrElse(tx -> {
            tx.setBlockchainTxId(blockchainTxId);
            tx.setBlockNumber(blockNumber);
            tx.setLedgerStatus(LedgerStatus.COMMITTED);
            tx.setVerificationStatus(parseVerificationStatus(verificationStatus));
            tx.setPayloadHash(payloadHash);
            tx.setRecordHash(recordHash);
            tx.setPreviousHash(previousHash);

            Transaction saved = transactionRepository.save(tx);

            metricsConfig.getBlockchainSubmissionCounter().increment();
            metricsConfig.getBlockchainVerificationCounter().increment();
            metricsConfig.recordBlockchainSubmission("success", saved.getTenantId());

            if (saved.getStatus() == TransactionStatus.VERIFIED) {
                eventPublisher.publishTransactionVerified(saved);
                metricsConfig.recordTransactionCreated("VERIFIED", saved.getTenantId());
                metricsConfig.recordAmountProcessed(saved.getAmount().doubleValue(), saved.getCurrency(), "VERIFIED");
            }

            log.info("Transaction {} committed to Fabric. blockchainTxId={} blockNumber={} verificationStatus={}",
                    saved.getTransactionId(), blockchainTxId, blockNumber, saved.getVerificationStatus());
        }, () -> log.warn("Received blockchain commit event for unknown transaction {}", transactionId));
    }

    @Transactional
    public void markLedgerUnavailable(String transactionId, String errorMessage) {
        transactionRepository.findByTransactionId(transactionId).ifPresent(tx -> {
            tx.setLedgerStatus(LedgerStatus.PENDING_LEDGER);
            tx.setVerificationStatus(VerificationStatus.UNAVAILABLE);
            transactionRepository.save(tx);

            metricsConfig.recordBlockchainSubmission("pending", tx.getTenantId());
            log.warn("Transaction {} remains in PENDING_LEDGER: {}", transactionId, errorMessage);
        });
    }

    @Transactional
    public void updateVerificationStatus(
            String transactionId,
            String verificationStatus,
            String payloadHash,
            String recordHash,
            String previousHash) {
        transactionRepository.findByTransactionId(transactionId).ifPresent(tx -> {
            VerificationStatus parsedStatus = parseVerificationStatus(verificationStatus);
            if (parsedStatus == tx.getVerificationStatus()
                    && equalsOrNull(payloadHash, tx.getPayloadHash())
                    && equalsOrNull(recordHash, tx.getRecordHash())
                    && equalsOrNull(previousHash, tx.getPreviousHash())) {
                return;
            }

            tx.setVerificationStatus(parsedStatus);
            if (payloadHash != null && !payloadHash.isBlank()) {
                tx.setPayloadHash(payloadHash);
            }
            if (recordHash != null && !recordHash.isBlank()) {
                tx.setRecordHash(recordHash);
            }
            if (previousHash != null && !previousHash.isBlank()) {
                tx.setPreviousHash(previousHash);
            }
            transactionRepository.save(tx);
            metricsConfig.getBlockchainVerificationCounter().increment();

            log.info("Transaction {} verification status refreshed to {}",
                    transactionId, tx.getVerificationStatus());
        });
    }

    private VerificationStatus parseVerificationStatus(String verificationStatus) {
        if (verificationStatus == null || verificationStatus.isBlank()) {
            return VerificationStatus.NOT_VERIFIED;
        }
        try {
            return VerificationStatus.valueOf(verificationStatus);
        } catch (IllegalArgumentException ex) {
            return VerificationStatus.UNAVAILABLE;
        }
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
                .ledgerStatus(tx.getLedgerStatus())
                .verificationStatus(tx.getVerificationStatus())
                .blockchainTxId(tx.getBlockchainTxId())
                .blockNumber(tx.getBlockNumber())
                .fraudScore(tx.getFraudScore())
                .fraudRiskLevel(tx.getFraudRiskLevel())
                .fraudDecision(tx.getFraudDecision())
                .fraudRecommendation(tx.getFraudRecommendation())
                .reviewRequired(tx.isReviewRequired())
                .triggeredRules(tx.getTriggeredRules())
                .explanations(tx.getExplanations())
                .payloadHash(tx.getPayloadHash())
                .recordHash(tx.getRecordHash())
                .previousHash(tx.getPreviousHash())
                .correlationId(tx.getCorrelationId())
                .createdAt(tx.getCreatedAt())
                .completedAt(tx.getCompletedAt())
                .build();
    }

    private Specification<Transaction> buildSpecification(String tenantId, TransactionQueryFilters filters) {
        return byTenant(tenantId)
                .and(hasSearch(filters != null ? filters.search() : null))
                .and(equalsStatus(filters != null ? filters.status() : null))
                .and(equalsType(filters != null ? filters.type() : null))
                .and(equalsLedgerStatus(filters != null ? filters.ledgerStatus() : null))
                .and(equalsVerificationStatus(filters != null ? filters.verificationStatus() : null))
                .and(createdAfter(filters != null ? filters.fromDate() : null))
                .and(createdBefore(filters != null ? filters.toDate() : null));
    }

    private Specification<Transaction> byTenant(String tenantId) {
        return (root, query, cb) -> cb.equal(root.get("tenantId"), tenantId);
    }

    private Specification<Transaction> hasSearch(String search) {
        if (search == null || search.isBlank()) {
            return null;
        }
        String pattern = "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("transactionId")), pattern),
                cb.like(cb.lower(root.get("fromAccount")), pattern),
                cb.like(cb.lower(root.get("toAccount")), pattern),
                cb.like(cb.lower(root.get("currency")), pattern),
                cb.like(cb.lower(root.get("correlationId")), pattern),
                cb.like(cb.lower(root.get("blockchainTxId")), pattern),
                cb.like(cb.lower(root.get("fraudRiskLevel")), pattern),
                cb.like(cb.lower(root.get("fraudDecision")), pattern)
        );
    }

    private Specification<Transaction> equalsStatus(TransactionStatus status) {
        return status == null ? null : (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    private Specification<Transaction> equalsType(TransactionType type) {
        return type == null ? null : (root, query, cb) -> cb.equal(root.get("type"), type);
    }

    private Specification<Transaction> equalsLedgerStatus(LedgerStatus ledgerStatus) {
        return ledgerStatus == null ? null : (root, query, cb) -> cb.equal(root.get("ledgerStatus"), ledgerStatus);
    }

    private Specification<Transaction> equalsVerificationStatus(VerificationStatus verificationStatus) {
        return verificationStatus == null
                ? null
                : (root, query, cb) -> cb.equal(root.get("verificationStatus"), verificationStatus);
    }

    private Specification<Transaction> createdAfter(LocalDateTime fromDate) {
        return fromDate == null ? null : (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("createdAt"), fromDate);
    }

    private Specification<Transaction> createdBefore(LocalDateTime toDate) {
        return toDate == null ? null : (root, query, cb) -> cb.lessThanOrEqualTo(root.get("createdAt"), toDate);
    }

    private <E extends Enum<E>> Map<String, Long> countStatuses(
            List<Transaction> transactions,
            Function<Transaction, E> extractor,
            E[] values) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (E value : values) {
            counts.put(value.name(), 0L);
        }

        Map<String, Long> observedCounts = transactions.stream()
                .map(extractor)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.groupingBy(Enum::name, LinkedHashMap::new, Collectors.counting()));

        observedCounts.forEach(counts::put);
        return counts;
    }

    private Map<String, Long> countFraudRiskLevels(List<Transaction> transactions) {
        Map<String, Long> distribution = new LinkedHashMap<>();
        distribution.put("LOW", 0L);
        distribution.put("MEDIUM", 0L);
        distribution.put("HIGH", 0L);
        distribution.put("CRITICAL", 0L);

        Map<String, Long> observed = transactions.stream()
                .map(Transaction::getFraudRiskLevel)
                .filter(level -> level != null && !level.isBlank())
                .map(level -> level.trim().toUpperCase(Locale.ROOT))
                .collect(Collectors.groupingBy(Function.identity(), LinkedHashMap::new, Collectors.counting()));

        observed.forEach(distribution::put);
        return distribution;
    }

    private BigDecimal sumAmounts(List<Transaction> transactions) {
        return transactions.stream()
                .map(Transaction::getAmount)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private List<TransactionStatsResponse.DailyVolumePoint> buildDailyVolume(
            LocalDateTime from,
            LocalDateTime to,
            List<Transaction> transactions) {
        LocalDate startDate = from.toLocalDate();
        LocalDate endDate = to.toLocalDate();
        Map<LocalDate, List<Transaction>> grouped = transactions.stream()
                .filter(tx -> tx.getCreatedAt() != null)
                .collect(Collectors.groupingBy(
                        tx -> tx.getCreatedAt().toLocalDate(),
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        List<TransactionStatsResponse.DailyVolumePoint> points = new ArrayList<>();
        for (LocalDate current = startDate; !current.isAfter(endDate); current = current.plusDays(1)) {
            List<Transaction> dailyTransactions = grouped.getOrDefault(current, List.of());
            points.add(TransactionStatsResponse.DailyVolumePoint.builder()
                    .date(current.toString())
                    .label(current.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.ENGLISH))
                    .transactionCount(dailyTransactions.size())
                    .totalAmount(sumAmounts(dailyTransactions))
                    .build());
        }
        return points;
    }

    private boolean equalsOrNull(String left, String right) {
        if (left == null || left.isBlank()) {
            return right == null || right.isBlank();
        }
        return left.equals(right);
    }

    private Map<String, String> resolveFraudMetadata(Transaction tx) {
        Map<String, String> resolved = new HashMap<>();
        if (tx.getMetadata() != null) {
            resolved.putAll(tx.getMetadata());
        }

        try {
            TenantServiceClient.ApiResponse<TenantServiceClient.TenantResponse> response =
                    tenantServiceClient.getTenant(tx.getTenantId());
            Map<String, String> tenantConfig = response != null && response.data() != null
                    ? response.data().config()
                    : Map.of();

            if (tenantConfig != null) {
                TENANT_FRAUD_CONFIG_KEYS.forEach(key -> {
                    String value = tenantConfig.get(key);
                    if (value != null && !value.isBlank()) {
                        resolved.putIfAbsent(key, value);
                    }
                });
            }
        } catch (Exception ex) {
            log.debug("Tenant config unavailable for tenant {}. Falling back to platform defaults: {}",
                    tx.getTenantId(), ex.getMessage());
        }

        return resolved;
    }

    private String resolveCorrelationId(String requestId) {
        if (requestId != null && !requestId.isBlank()) {
            return requestId.trim();
        }
        return UUID.randomUUID().toString();
    }
}
