package com.bbss.transaction.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.Getter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Custom business metrics configuration for Transaction Service.
 *
 * <p><strong>Metrics Categories:</strong></p>
 * <ul>
 *   <li><strong>Transaction Processing</strong>: Timers for create, verify, processing pipeline</li>
 *   <li><strong>Fraud Detection</strong>: Fraud score distribution, risk level counters, blocked transaction rate</li>
 *   <li><strong>Blockchain Integration</strong>: Blockchain submission latency, success/failure rates</li>
 *   <li><strong>Business KPIs</strong>: Transaction volume, total amount processed, average transaction value</li>
 * </ul>
 *
 * <p><strong>SLA Tracking:</strong></p>
 * <ul>
 *   <li>Transaction processing p95 < 500ms</li>
 *   <li>Fraud scoring latency p95 < 100ms</li>
 *   <li>Blockchain submission p95 < 2000ms</li>
 * </ul>
 *
 * @see io.micrometer.core.instrument.MeterRegistry
 */
@Configuration
@Getter
public class MetricsConfig {

    // =========================================================================
    // Transaction Processing Metrics
    // =========================================================================

    /**
     * Timer for overall transaction creation (from API request to initial persistence).
     * <p><strong>SLA Target</strong>: p95 < 200ms</p>
     */
    private final Timer transactionCreationTimer;

    /**
     * Timer for fraud detection scoring latency.
     * <p><strong>SLA Target</strong>: p95 < 100ms</p>
     */
    private final Timer fraudScoringTimer;

    /**
     * Timer for blockchain submission (including network latency and consensus).
     * <p><strong>SLA Target</strong>: p95 < 2000ms</p>
     */
    private final Timer blockchainSubmissionTimer;

    /**
     * Timer for end-to-end transaction processing (create → fraud check → blockchain → complete).
     * <p><strong>SLA Target</strong>: p95 < 3000ms</p>
     */
    private final Timer transactionProcessingTimer;

    // =========================================================================
    // Fraud Detection Metrics
    // =========================================================================

    /**
     * Distribution summary for fraud scores (0.0 - 1.0).
     * <p>Tracks min, max, mean, p50, p95, p99 fraud scores across all transactions.</p>
     */
    private final DistributionSummary fraudScoreDistribution;

    /**
     * Counter for transactions blocked due to high fraud risk.
     * <p>Use with transaction.created counter to calculate blocked rate.</p>
     */
    private final Counter transactionsBlockedCounter;

    /**
     * Counter for fraud alerts generated (fraud score > threshold).
     */
    private final Counter fraudAlertsCounter;

    // =========================================================================
    // Business KPI Metrics
    // =========================================================================

    /**
     * Counter for total transactions created.
     * <p>Tagged by: status (PENDING, VERIFIED, BLOCKED), tenant_id</p>
     */
    private final Counter transactionCreatedCounter;

    /**
     * Distribution summary for transaction amounts (in base currency units).
     * <p>Tracks transaction value distribution for financial reporting.</p>
     */
    private final DistributionSummary transactionAmountDistribution;

    /**
     * Counter for total currency amount processed (sum of all transaction amounts).
     * <p>Tagged by: currency, status</p>
     */
    private final Counter totalAmountProcessedCounter;

    // =========================================================================
    // Blockchain Integration Metrics
    // =========================================================================

    /**
     * Counter for blockchain submission outcomes.
     * <p>Tagged by: result (success, failure, circuit_open)</p>
     */
    private final Counter blockchainSubmissionCounter;

    /**
     * Counter for blockchain verification events (async callback from blockchain-service).
     */
    private final Counter blockchainVerificationCounter;

    // =========================================================================
    // Constructor with Metric Registration
    // =========================================================================

    private final MeterRegistry meterRegistry;

    public MetricsConfig(MeterRegistry registry) {
        this.meterRegistry = registry;
        
        // Transaction Processing Timers
        this.transactionCreationTimer = Timer.builder("bbss.transaction.creation")
                .description("Time to create and persist a new transaction")
                .tag("operation", "create")
                .publishPercentiles(0.5, 0.95, 0.99)
                .publishPercentileHistogram()
                .minimumExpectedValue(Duration.ofMillis(10))
                .maximumExpectedValue(Duration.ofMillis(500))
                .serviceLevelObjectives(
                        Duration.ofMillis(50),   // p50 target
                        Duration.ofMillis(100),  // p75 target
                        Duration.ofMillis(200)   // p95 target
                )
                .register(registry);

        this.fraudScoringTimer = Timer.builder("bbss.fraud.scoring")
                .description("Time for fraud detection service to score a transaction")
                .tag("operation", "score")
                .publishPercentiles(0.5, 0.95, 0.99)
                .publishPercentileHistogram()
                .minimumExpectedValue(Duration.ofMillis(5))
                .maximumExpectedValue(Duration.ofMillis(200))
                .serviceLevelObjectives(
                        Duration.ofMillis(25),   // p50 target
                        Duration.ofMillis(50),   // p75 target
                        Duration.ofMillis(100)   // p95 target
                )
                .register(registry);

        this.blockchainSubmissionTimer = Timer.builder("bbss.blockchain.submission")
                .description("Time to submit transaction to blockchain (including consensus)")
                .tag("operation", "submit")
                .publishPercentiles(0.5, 0.95, 0.99)
                .publishPercentileHistogram()
                .minimumExpectedValue(Duration.ofMillis(100))
                .maximumExpectedValue(Duration.ofSeconds(5))
                .serviceLevelObjectives(
                        Duration.ofMillis(500),   // p50 target
                        Duration.ofMillis(1000),  // p75 target
                        Duration.ofMillis(2000)   // p95 target
                )
                .register(registry);

        this.transactionProcessingTimer = Timer.builder("bbss.transaction.processing")
                .description("End-to-end transaction processing time")
                .tag("operation", "process")
                .publishPercentiles(0.5, 0.95, 0.99)
                .publishPercentileHistogram()
                .minimumExpectedValue(Duration.ofMillis(200))
                .maximumExpectedValue(Duration.ofSeconds(10))
                .serviceLevelObjectives(
                        Duration.ofMillis(1000),  // p50 target
                        Duration.ofMillis(2000),  // p75 target
                        Duration.ofMillis(3000)   // p95 target
                )
                .register(registry);

        // Fraud Detection Metrics
        this.fraudScoreDistribution = DistributionSummary.builder("bbss.fraud.score")
                .description("Distribution of fraud scores (0.0 = safe, 1.0 = fraudulent)")
                .baseUnit("score")
                .publishPercentiles(0.5, 0.75, 0.95, 0.99)
                .publishPercentileHistogram()
                .minimumExpectedValue(0.1)
                .maximumExpectedValue(1.0)
                .serviceLevelObjectives(0.3, 0.5, 0.7, 0.9)  // Risk level thresholds
                .register(registry);

        this.transactionsBlockedCounter = Counter.builder("bbss.transaction.blocked")
                .description("Number of transactions blocked due to high fraud risk")
                .tag("reason", "fraud")
                .register(registry);

        this.fraudAlertsCounter = Counter.builder("bbss.fraud.alerts")
                .description("Number of fraud alerts generated")
                .register(registry);

        // Business KPI Metrics
        this.transactionCreatedCounter = Counter.builder("bbss.transaction.created")
                .description("Total number of transactions created")
                .register(registry);

        this.transactionAmountDistribution = DistributionSummary.builder("bbss.transaction.amount")
                .description("Distribution of transaction amounts")
                .baseUnit("currency_units")
                .publishPercentiles(0.5, 0.75, 0.95, 0.99)
                .publishPercentileHistogram()
                .minimumExpectedValue(1.0)
                .maximumExpectedValue(1000000.0)
                .serviceLevelObjectives(100.0, 1000.0, 10000.0, 100000.0)
                .register(registry);

        this.totalAmountProcessedCounter = Counter.builder("bbss.transaction.total_amount")
                .description("Total currency amount processed")
                .baseUnit("currency_units")
                .register(registry);

        // Blockchain Integration Metrics
        this.blockchainSubmissionCounter = Counter.builder("bbss.blockchain.submissions")
                .description("Number of blockchain submissions")
                .register(registry);

        this.blockchainVerificationCounter = Counter.builder("bbss.blockchain.verifications")
                .description("Number of blockchain verification events received")
                .register(registry);
    }

    // =========================================================================
    // Utility Methods for Tagged Metrics
    // =========================================================================

    /**
     * Record a transaction creation with status tag.
     */
    public void recordTransactionCreated(String status, String tenantId) {
       Counter.builder("bbss.transaction.created.tagged")
                .tag("status", status)
                .tag("tenant_id", tenantId)
                .description("Transactions created by status and tenant")
                .register(meterRegistry)
                .increment();
    }

    /**
     * Record fraud score for distribution tracking.
     */
    public void recordFraudScore(double score, String riskLevel) {
        DistributionSummary.builder("bbss.fraud.score.tagged")
                .tag("risk_level", riskLevel)
                .description("Fraud scores by risk level")
                .register(meterRegistry)
                .record(score);
    }

    /**
     * Record blockchain submission outcome.
     */
    public void recordBlockchainSubmission(String result, String tenantId) {
        Counter.builder("bbss.blockchain.submissions.tagged")
                .tag("result", result)
                .tag("tenant_id", tenantId)
                .description("Blockchain submissions by result")
                .register(meterRegistry)
                .increment();
    }

    /**
     * Record transaction amount processed.
     */
    public void recordAmountProcessed(double amount, String currency, String status) {
        Counter.builder("bbss.transaction.total_amount.tagged")
                .tag("currency", currency)
                .tag("status", status)
                .description("Total amount processed by currency and status")
                .baseUnit("currency_units")
                .register(meterRegistry)
                .increment(amount);
    }
}
