package com.bbss.blockchain.config;

import java.time.Duration;

import org.springframework.context.annotation.Configuration;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

/**
 * Custom metrics configuration for blockchain-service.
 *
 * <p>Defines enterprise-grade metrics for Hyperledger Fabric operations tracking:
 * <ul>
 *   <li><strong>Fabric Gateway Timers</strong>: submitTransaction (p95 < 3000ms), evaluateTransaction (p95 < 500ms)</li>
 *   <li><strong>Chaincode Invocation Counters</strong>: Success/failure/circuit_open rates by chaincode and operation type</li>
 *   <li><strong>Business Metrics</strong>: Endorsement count, commit count, payload size distribution</li>
 * </ul>
 *
 * <p><strong>Prometheus Endpoints:</strong></p>
 * <ul>
 *   <li>Metrics: http://localhost:8084/actuator/prometheus</li>
 *   <li>Sample queries:
 *     <pre>
 *     # P95 submit transaction latency
 *     histogram_quantile(0.95, bbss_blockchain_fabric_submit_seconds_bucket)
 *     
 *     # Chaincode invocation success rate
 *     sum(rate(bbss_blockchain_fabric_invocations_total{result="success"}[5m]))
 *       /
 *     sum(rate(bbss_blockchain_fabric_invocations_total[5m]))
 *     
 *     # Circuit breaker open rate
 *     sum(rate(bbss_blockchain_fabric_invocations_total{result="circuit_open"}[5m]))
 *     </pre>
 *   </li>
 * </ul>
 *
 * <p><strong>SLA Targets:</strong></p>
 * <ul>
 *   <li>Submit transaction p95: < 3000ms</li>
 *   <li>Evaluate transaction p95: < 500ms</li>
 *   <li>Endorsement success rate: > 99%</li>
 *   <li>Circuit breaker open rate: < 1%</li>
 * </ul>
 *
 * @see com.bbss.blockchain.service.FabricGatewayService
 */
@Configuration
public class MetricsConfig {

    // Fabric Gateway operation timers
    private final Timer fabricSubmitTimer;
    private final Timer fabricEvaluateTimer;

    // Chaincode invocation counters
    private final Counter fabricInvocationsCounter;
    private final Counter fabricEndorsementsCounter;
    private final Counter fabricCommitsCounter;
    private final Counter circuitBreakerEventsCounter;

    // Payload size tracking
    private final DistributionSummary payloadSizeDistribution;

    // Registry for creating tagged metrics
    private final MeterRegistry meterRegistry;

    public MetricsConfig(MeterRegistry registry) {
        this.meterRegistry = registry;

        // =====================================================================
        // 1. Fabric Gateway Submit Transaction Timer
        // =====================================================================
        // Tracks end-to-end time for submitTransaction (endorsement + ordering + commit)
        // Target: p95 < 3000ms (includes network latency, peer processing, orderer consensus, commit wait)
        this.fabricSubmitTimer = Timer.builder("bbss.blockchain.fabric.submit")
                .description("Time to submit transaction to Fabric (endorse + order + commit)")
                .publishPercentiles(0.5, 0.95, 0.99)
                .publishPercentileHistogram()
                .serviceLevelObjectives(
                        Duration.ofMillis(500),   // p50 target
                        Duration.ofMillis(1500),  // p75 target
                        Duration.ofMillis(3000),  // p95 target
                        Duration.ofMillis(5000)   // p99 ceiling
                )
                .minimumExpectedValue(Duration.ofMillis(100))
                .maximumExpectedValue(Duration.ofSeconds(10))
                .register(registry);

        // =====================================================================
        // 2. Fabric Gateway Evaluate Transaction Timer
        // =====================================================================
        // Tracks query time for evaluateTransaction (single peer query, no consensus)
        // Target: p95 < 500ms (much faster as it's read-only without ordering)
        this.fabricEvaluateTimer = Timer.builder("bbss.blockchain.fabric.evaluate")
                .description("Time to evaluate (query) transaction on Fabric peer")
                .publishPercentiles(0.5, 0.95, 0.99)
                .publishPercentileHistogram()
                .serviceLevelObjectives(
                        Duration.ofMillis(50),   // p50 target
                        Duration.ofMillis(200),  // p75 target
                        Duration.ofMillis(500),  // p95 target
                        Duration.ofMillis(1000)  // p99 ceiling
                )
                .minimumExpectedValue(Duration.ofMillis(10))
                .maximumExpectedValue(Duration.ofSeconds(2))
                .register(registry);

        // =====================================================================
        // 3. Chaincode Invocation Counter (Base)
        // =====================================================================
        // Base counter for all chaincode invocations (tagged variants created dynamically)
        this.fabricInvocationsCounter = Counter.builder("bbss.blockchain.fabric.invocations")
                .description("Total chaincode invocations (submit + evaluate)")
                .register(registry);

        // =====================================================================
        // 4. Fabric Endorsements Counter
        // =====================================================================
        // Successful peer endorsements (only for submit transactions)
        this.fabricEndorsementsCounter = Counter.builder("bbss.blockchain.fabric.endorsements")
                .description("Successful peer endorsements for transaction proposals")
                .register(registry);

        // =====================================================================
        // 5. Fabric Commits Counter
        // =====================================================================
        // Successful block commits (transaction included in blockchain)
        this.fabricCommitsCounter = Counter.builder("bbss.blockchain.fabric.commits")
                .description("Transactions successfully committed to blockchain")
                .register(registry);

        // =====================================================================
        // 6. Circuit Breaker Events Counter
        // =====================================================================
        // Circuit breaker state transitions and events
        this.circuitBreakerEventsCounter = Counter.builder("bbss.blockchain.fabric.circuit_breaker")
                .description("Circuit breaker events for Fabric Gateway")
                .register(registry);

        // =====================================================================
        // 7. Payload Size Distribution
        // =====================================================================
        // Track chaincode response payload sizes
        this.payloadSizeDistribution = DistributionSummary.builder("bbss.blockchain.fabric.payload_size")
                .description("Chaincode response payload size in bytes")
                .baseUnit("bytes")
                .publishPercentiles(0.5, 0.75, 0.95, 0.99)
                .serviceLevelObjectives(1024.0, 10240.0, 102400.0, 1048576.0)  // 1KB, 10KB, 100KB, 1MB
                .minimumExpectedValue(1.0)
                .maximumExpectedValue(10485760.0)  // 10MB
                .register(registry);
    }

    // =========================================================================
    // Getters for injecting meters into services
    // =========================================================================

    public Timer getFabricSubmitTimer() {
        return fabricSubmitTimer;
    }

    public Timer getFabricEvaluateTimer() {
        return fabricEvaluateTimer;
    }

    public Counter getFabricInvocationsCounter() {
        return fabricInvocationsCounter;
    }

    public Counter getFabricEndorsementsCounter() {
        return fabricEndorsementsCounter;
    }

    public Counter getFabricCommitsCounter() {
        return fabricCommitsCounter;
    }

    public Counter getCircuitBreakerEventsCounter() {
        return circuitBreakerEventsCounter;
    }

    public DistributionSummary getPayloadSizeDistribution() {
        return payloadSizeDistribution;
    }

    // =========================================================================
    // Utility methods for creating tagged metrics dynamically
    // =========================================================================

    /**
     * Record a chaincode invocation with result and metadata tags.
     *
     * <p>Creates dynamic counter: bbss.blockchain.fabric.invocations.tagged</p>
     * <p>Tags: type={submit,evaluate}, chaincode={transaction-cc,audit-cc}, result={success,failure,circuit_open}</p>
     *
     * @param type the operation type ("submit" or "evaluate")
     * @param chaincode the chaincode name ("transaction-cc" or "audit-cc")
     * @param result the result status ("success", "failure", "circuit_open")
     */
    public void recordInvocation(String type, String chaincode, String result) {
        Counter.builder("bbss.blockchain.fabric.invocations.tagged")
                .tag("type", type)
                .tag("chaincode", chaincode)
                .tag("result", result)
                .description("Chaincode invocations by type, chaincode, and result")
                .register(meterRegistry)
                .increment();
    }

    /**
     * Record circuit breaker state transition.
     *
     * <p>Creates dynamic counter: bbss.blockchain.fabric.circuit_breaker.tagged</p>
     * <p>Tags: state={closed,open,half_open}, event={state_transition,success_rate_exceeded,error_rate_exceeded}</p>
     *
     * @param state the circuit breaker state
     * @param event the event type
     */
    public void recordCircuitBreakerEvent(String state, String event) {
        Counter.builder("bbss.blockchain.fabric.circuit_breaker.tagged")
                .tag("state", state)
                .tag("event", event)
                .description("Circuit breaker events by state and event type")
                .register(meterRegistry)
                .increment();
    }

    /**
     * Record chaincode endorsement (successful peer signature).
     *
     * <p>Creates dynamic counter: bbss.blockchain.fabric.endorsements.tagged</p>
     * <p>Tags: chaincode={transaction-cc,audit-cc}, function={createTransaction,recordAudit,...}</p>
     *
     * @param chaincode the chaincode name
     * @param function the chaincode function invoked
     */
    public void recordEndorsement(String chaincode, String function) {
        Counter.builder("bbss.blockchain.fabric.endorsements.tagged")
                .tag("chaincode", chaincode)
                .tag("function", function)
                .description("Successful endorsements by chaincode and function")
                .register(meterRegistry)
                .increment();
    }

    /**
     * Record successful blockchain commit.
     *
     * <p>Creates dynamic counter: bbss.blockchain.fabric.commits.tagged</p>
     * <p>Tags: chaincode={transaction-cc,audit-cc}, function={createTransaction,recordAudit,...}</p>
     *
     * @param chaincode the chaincode name
     * @param function the chaincode function invoked
     */
    public void recordCommit(String chaincode, String function) {
        Counter.builder("bbss.blockchain.fabric.commits.tagged")
                .tag("chaincode", chaincode)
                .tag("function", function)
                .description("Successful commits by chaincode and function")
                .register(meterRegistry)
                .increment();
    }
}
