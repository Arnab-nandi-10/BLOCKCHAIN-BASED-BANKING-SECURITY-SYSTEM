package com.bbss.blockchain.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.hyperledger.fabric.client.CommitException;
import org.hyperledger.fabric.client.Contract;
import org.hyperledger.fabric.client.GatewayException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import com.bbss.blockchain.config.MetricsConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;

/**
 * Low-level service that wraps Hyperledger Fabric chaincode invocations.
 *
 * <p>Provides two modes of interaction:
 * <ul>
 *   <li><strong>submitTransaction</strong> — sends a transaction proposal to peers for
 *       endorsement, submits the endorsed transaction to the orderer, and waits for
 *       block commitment. Use this for chaincode functions that mutate state.</li>
 *   <li><strong>evaluateTransaction</strong> — queries a single peer without going
 *       through the orderer. Use this for read-only chaincode functions.</li>
 * </ul>
 * </p>
 *
 * <p>Both chaincodes ({@code transactionContract} and {@code auditContract}) are
 * injected by qualifier so the correct contract is used for each invocation.</p>
 */

@ConditionalOnProperty(name = "fabric.enabled", havingValue = "true", matchIfMissing = false)
@Service
@Slf4j
public class FabricGatewayService {

    private final Contract transactionContract;
    private final Contract auditContract;
    private final ObjectMapper objectMapper;
    private final MetricsConfig metricsConfig;

    public FabricGatewayService(
            @Qualifier("transactionContract") Contract transactionContract,
            @Qualifier("auditContract")       Contract auditContract,
            ObjectMapper objectMapper,
            MetricsConfig metricsConfig) {
        this.transactionContract = transactionContract;
        this.auditContract       = auditContract;
        this.objectMapper        = objectMapper;
        this.metricsConfig       = metricsConfig;
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Submit a state-changing transaction to the Fabric network with circuit breaker protection.
     *
     * <p>The call blocks until the transaction is committed in a block or an
     * error occurs. On success the raw chaincode response bytes are returned.</p>
     *
     * <p><strong>Fault Tolerance:</strong></p>
     * <ul>
     *   <li><strong>Circuit Breaker</strong>: Opens after 30% failure rate, stays open for 20s</li>
     *   <li><strong>Bulkhead</strong>: Limits concurrent calls to 5 to prevent connection exhaustion</li>
     *   <li><strong>Fallback</strong>: Returns empty array when circuit is OPEN</li>
     * </ul>
     *
     * @param chaincode the logical chaincode name — {@code "transaction-cc"} or
     *                  {@code "audit-cc"}
     * @param function  the chaincode function to invoke
     * @param args      string arguments passed to the chaincode function
     * @return raw byte array returned by the chaincode (may be empty)
     * @throws BlockchainServiceException on any Fabric or commit failure (when circuit is CLOSED)
     */
    @CircuitBreaker(name = "fabricGateway", fallbackMethod = "submitTransactionFallback")
    @Bulkhead(name = "fabricGateway")
    public byte[] submitTransaction(String chaincode, String function, String... args) {
        final long startTime = System.nanoTime();
        Contract contract = resolveContract(chaincode);
        try {
            log.debug("Submitting tx to chaincode={} function={}", chaincode, function);
            byte[] result = contract.submitTransaction(function, args);
            
            // Record successful submit metrics
            metricsConfig.getFabricSubmitTimer().record(System.nanoTime() - startTime,
                    java.util.concurrent.TimeUnit.NANOSECONDS);
            metricsConfig.getFabricInvocationsCounter().increment();
            metricsConfig.recordInvocation("submit", chaincode, "success");
            metricsConfig.recordEndorsement(chaincode, function);
            metricsConfig.recordCommit(chaincode, function);
            metricsConfig.getFabricEndorsementsCounter().increment();
            metricsConfig.getFabricCommitsCounter().increment();
            
            // Track payload size
            if (result != null && result.length > 0) {
                metricsConfig.getPayloadSizeDistribution().record(result.length);
            }
            
            log.debug("submitTransaction succeeded: chaincode={} function={}", chaincode, function);
            return result;
        } catch (CommitException ex) {
            // Record timing even on failure
            metricsConfig.getFabricSubmitTimer().record(System.nanoTime() - startTime,
                    java.util.concurrent.TimeUnit.NANOSECONDS);
            metricsConfig.recordInvocation("submit", chaincode, "failure");
            
            log.error("Transaction commit failed: chaincode={} function={} txId={} status={} message={}",
                    chaincode, function, ex.getTransactionId(), ex.getCode(), ex.getMessage(), ex);
            throw new BlockchainServiceException(
                    "Chaincode commit failed for " + function + ": " + ex.getMessage(), ex);
        } catch (GatewayException ex) {
            // Record timing even on failure
            metricsConfig.getFabricSubmitTimer().record(System.nanoTime() - startTime,
                    java.util.concurrent.TimeUnit.NANOSECONDS);
            metricsConfig.recordInvocation("submit", chaincode, "failure");
            
            log.error("Gateway error during submitTransaction: chaincode={} function={} details={}",
                    chaincode, function, ex.getMessage(), ex);
            throw new BlockchainServiceException(
                    "Gateway error for " + function + ": " + ex.getMessage(), ex);
        } catch (Exception ex) {
            // Record timing even on failure
            metricsConfig.getFabricSubmitTimer().record(System.nanoTime() - startTime,
                    java.util.concurrent.TimeUnit.NANOSECONDS);
            metricsConfig.recordInvocation("submit", chaincode, "failure");
            
            log.error("Unexpected error in submitTransaction: chaincode={} function={}",
                    chaincode, function, ex);
            throw new BlockchainServiceException(
                    "Unexpected error submitting transaction " + function + ": " + ex.getMessage(), ex);
        }
    }

    /**
     * Fallback method for {@link #submitTransaction} when circuit breaker is OPEN.
     *
     * <p>Returns an empty byte array to signal that the transaction write is pending.
     * The caller should interpret this as "PENDING_VERIFICATION" status.</p>
     *
     * @param chaincode the chaincode name (for logging)
     * @param function the function name (for logging)
     * @param args the original arguments (for logging)
     * @param throwable the exception that triggered the fallback (circuit open or bulkhead full)
     * @return empty byte array signaling pending state
     */
    private byte[] submitTransactionFallback(String chaincode, String function, String[] args, Throwable throwable) {
        log.warn("Circuit breaker OPEN or bulkhead FULL for submitTransaction: chaincode={} function={} reason={}",
                chaincode, function, throwable.getMessage());
        
        // Record circuit breaker open metrics
        metricsConfig.recordInvocation("submit", chaincode, "circuit_open");
        metricsConfig.recordCircuitBreakerEvent("open", "fallback_triggered");
        
        // Return empty response to signal pending state to caller
        // ChaincodeInvokerService will handle this as a graceful degradation
        return new byte[0];
    }

    /**
     * Evaluate (query) a chaincode function without submitting a transaction, with circuit breaker protection.
     *
     * <p><strong>Fault Tolerance:</strong></p>
     * <ul>
     *   <li><strong>Circuit Breaker</strong>: Shares 'fabricGateway' instance with submitTransaction</li>
     *   <li><strong>Bulkhead</strong>: Limited concurrency to protect connection pool</li>
     *   <li><strong>Fallback</strong>: Returns empty array when Fabric Gateway unavailable</li>
     * </ul>
     *
     * @param chaincode the logical chaincode name
     * @param function  the chaincode function to evaluate
     * @param args      string arguments passed to the chaincode function
     * @return raw byte array returned by the chaincode
     * @throws BlockchainServiceException on any Fabric query failure (when circuit is CLOSED)
     */
    @CircuitBreaker(name = "fabricGateway", fallbackMethod = "evaluateTransactionFallback")
    @Bulkhead(name = "fabricGateway")
    public byte[] evaluateTransaction(String chaincode, String function, String... args) {
        final long startTime = System.nanoTime();
        Contract contract = resolveContract(chaincode);
        try {
            log.debug("Evaluating query: chaincode={} function={}", chaincode, function);
            byte[] result = contract.evaluateTransaction(function, args);
            
            // Record successful evaluate metrics
            metricsConfig.getFabricEvaluateTimer().record(System.nanoTime() - startTime,
                    java.util.concurrent.TimeUnit.NANOSECONDS);
            metricsConfig.getFabricInvocationsCounter().increment();
            metricsConfig.recordInvocation("evaluate", chaincode, "success");
            
            // Track payload size
            if (result != null && result.length > 0) {
                metricsConfig.getPayloadSizeDistribution().record(result.length);
            }
            
            log.debug("evaluateTransaction succeeded: chaincode={} function={}", chaincode, function);
            return result;
        } catch (GatewayException ex) {
            // Record timing even on failure
            metricsConfig.getFabricEvaluateTimer().record(System.nanoTime() - startTime,
                    java.util.concurrent.TimeUnit.NANOSECONDS);
            metricsConfig.recordInvocation("evaluate", chaincode, "failure");
            
            log.error("Gateway error during evaluateTransaction: chaincode={} function={} message={}",
                    chaincode, function, ex.getMessage(), ex);
            throw new BlockchainServiceException(
                    "Gateway query error for " + function + ": " + ex.getMessage(), ex);
        } catch (Exception ex) {
            // Record timing even on failure
            metricsConfig.getFabricEvaluateTimer().record(System.nanoTime() - startTime,
                    java.util.concurrent.TimeUnit.NANOSECONDS);
            metricsConfig.recordInvocation("evaluate", chaincode, "failure");
            
            log.error("Unexpected error in evaluateTransaction: chaincode={} function={}",
                    chaincode, function, ex);
            throw new BlockchainServiceException(
                    "Unexpected error querying " + function + ": " + ex.getMessage(), ex);
        }
    }

    /**
     * Fallback method for {@link #evaluateTransaction} when circuit breaker is OPEN.
     *
     * @param chaincode the chaincode name (for logging)
     * @param function the function name (for logging)
     * @param args the original arguments (for logging)
     * @param throwable the exception that triggered the fallback
     * @return empty byte array signaling unavailable data
     */
    private byte[] evaluateTransactionFallback(String chaincode, String function, String[] args, Throwable throwable) {
        log.warn("Circuit breaker OPEN or bulkhead FULL for evaluateTransaction: chaincode={} function={} reason={}",
                chaincode, function, throwable.getMessage());
        
        // Record circuit breaker open metrics
        metricsConfig.recordInvocation("evaluate", chaincode, "circuit_open");
        metricsConfig.recordCircuitBreakerEvent("open", "fallback_triggered");
        
        // Return empty response to signal data unavailable
        return new byte[0];
    }

    /**
     * Deserialise a raw chaincode response into a {@link JsonNode}.
     *
     * @param response raw bytes returned by the chaincode
     * @return parsed JSON node
     * @throws BlockchainServiceException when the bytes are not valid JSON
     */
    public JsonNode parseJsonResponse(byte[] response) {
        if (response == null || response.length == 0) {
            return objectMapper.createObjectNode();
        }
        try {
            String json = new String(response, StandardCharsets.UTF_8);
            return objectMapper.readTree(json);
        } catch (IOException ex) {
            log.error("Failed to parse chaincode response as JSON: {}", ex.getMessage());
            throw new BlockchainServiceException(
                    "Failed to parse chaincode JSON response: " + ex.getMessage(), ex);
        }
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private Contract resolveContract(String chaincode) {
        return switch (chaincode) {
            case "transaction-cc" -> transactionContract;
            case "audit-cc"       -> auditContract;
            default -> throw new BlockchainServiceException(
                    "Unknown chaincode identifier: " + chaincode);
        };
    }

    // -------------------------------------------------------------------------
    // Nested exception class
    // -------------------------------------------------------------------------

    /**
     * Unchecked wrapper for Fabric-related exceptions to avoid propagating
     * checked exceptions through every layer of the service.
     */
    public static class BlockchainServiceException extends RuntimeException {

        public BlockchainServiceException(String message) {
            super(message);
        }

        public BlockchainServiceException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
