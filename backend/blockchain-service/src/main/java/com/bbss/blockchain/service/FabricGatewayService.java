package com.bbss.blockchain.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import org.hyperledger.fabric.client.CommitStatusException;
import org.hyperledger.fabric.client.Contract;
import org.hyperledger.fabric.client.EndorseException;
import org.hyperledger.fabric.client.GatewayException;
import org.hyperledger.fabric.client.Status;
import org.hyperledger.fabric.client.SubmitException;
import org.hyperledger.fabric.client.SubmittedTransaction;
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
            @Qualifier("auditContract") Contract auditContract,
            ObjectMapper objectMapper,
            MetricsConfig metricsConfig) {
        this.transactionContract = transactionContract;
        this.auditContract = auditContract;
        this.objectMapper = objectMapper;
        this.metricsConfig = metricsConfig;
    }

    @CircuitBreaker(name = "fabricGateway", fallbackMethod = "submitTransactionFallback")
    @Bulkhead(name = "fabricGateway")
    public FabricSubmitResult submitTransaction(String chaincode, String function, String... args) {
        final long startTime = System.nanoTime();
        Contract contract = resolveContract(chaincode);

        try {
            log.debug("Submitting tx to chaincode={} function={}", chaincode, function);

            SubmittedTransaction submitted = contract.newProposal(function)
                    .addArguments(args)
                    .build()
                    .endorse()
                    .submitAsync();

            byte[] result = submitted.getResult();
            Status status = submitted.getStatus();
            if (!status.isSuccessful()) {
                throw new BlockchainServiceException(
                        "Fabric commit status was not successful for " + function + ": " + status.getCode());
            }

            metricsConfig.getFabricSubmitTimer().record(System.nanoTime() - startTime,
                    java.util.concurrent.TimeUnit.NANOSECONDS);
            metricsConfig.getFabricInvocationsCounter().increment();
            metricsConfig.recordInvocation("submit", chaincode, "success");
            metricsConfig.recordEndorsement(chaincode, function);
            metricsConfig.recordCommit(chaincode, function);
            metricsConfig.getFabricEndorsementsCounter().increment();
            metricsConfig.getFabricCommitsCounter().increment();

            if (result != null && result.length > 0) {
                metricsConfig.getPayloadSizeDistribution().record(result.length);
            }

            String blockNumber = Long.toUnsignedString(status.getBlockNumber());
            String transactionId = status.getTransactionId();

            log.debug("submitTransaction succeeded: chaincode={} function={} txId={} blockNumber={}",
                    chaincode, function, transactionId, blockNumber);

            return new FabricSubmitResult(result, transactionId, blockNumber, true);
        } catch (CommitStatusException | SubmitException | EndorseException ex) {
            metricsConfig.getFabricSubmitTimer().record(System.nanoTime() - startTime,
                    java.util.concurrent.TimeUnit.NANOSECONDS);
            metricsConfig.recordInvocation("submit", chaincode, "failure");

            log.error("Fabric submit failed: chaincode={} function={} message={}",
                    chaincode, function, ex.getMessage(), ex);
            throw new BlockchainServiceException(
                    "Fabric submit failed for " + function + ": " + ex.getMessage(), ex);
        } catch (RuntimeException ex) {
            metricsConfig.getFabricSubmitTimer().record(System.nanoTime() - startTime,
                    java.util.concurrent.TimeUnit.NANOSECONDS);
            metricsConfig.recordInvocation("submit", chaincode, "failure");

            log.error("Unexpected error in submitTransaction: chaincode={} function={}",
                    chaincode, function, ex);
            throw new BlockchainServiceException(
                    "Unexpected error submitting transaction " + function + ": " + ex.getMessage(), ex);
        }
    }

    @SuppressWarnings("unused")
    private FabricSubmitResult submitTransactionFallback(
            String chaincode,
            String function,
            String[] args,
            Throwable throwable) {
        log.warn("Circuit breaker OPEN or bulkhead FULL for submitTransaction: chaincode={} function={} args={} reason={}",
                chaincode, function, Arrays.toString(args), throwable.getMessage());

        metricsConfig.recordInvocation("submit", chaincode, "circuit_open");
        metricsConfig.recordCircuitBreakerEvent("open", "fallback_triggered");
        return FabricSubmitResult.pending();
    }

    @CircuitBreaker(name = "fabricGateway", fallbackMethod = "evaluateTransactionFallback")
    @Bulkhead(name = "fabricGateway")
    public byte[] evaluateTransaction(String chaincode, String function, String... args) {
        final long startTime = System.nanoTime();
        Contract contract = resolveContract(chaincode);
        try {
            log.debug("Evaluating query: chaincode={} function={}", chaincode, function);
            byte[] result = contract.evaluateTransaction(function, args);

            metricsConfig.getFabricEvaluateTimer().record(System.nanoTime() - startTime,
                    java.util.concurrent.TimeUnit.NANOSECONDS);
            metricsConfig.getFabricInvocationsCounter().increment();
            metricsConfig.recordInvocation("evaluate", chaincode, "success");

            if (result != null && result.length > 0) {
                metricsConfig.getPayloadSizeDistribution().record(result.length);
            }

            log.debug("evaluateTransaction succeeded: chaincode={} function={}", chaincode, function);
            return result;
        } catch (GatewayException ex) {
            metricsConfig.getFabricEvaluateTimer().record(System.nanoTime() - startTime,
                    java.util.concurrent.TimeUnit.NANOSECONDS);
            metricsConfig.recordInvocation("evaluate", chaincode, "failure");

            log.error("Gateway error during evaluateTransaction: chaincode={} function={} message={}",
                    chaincode, function, ex.getMessage(), ex);
            throw new BlockchainServiceException(
                    "Gateway query error for " + function + ": " + ex.getMessage(), ex);
        } catch (RuntimeException ex) {
            metricsConfig.getFabricEvaluateTimer().record(System.nanoTime() - startTime,
                    java.util.concurrent.TimeUnit.NANOSECONDS);
            metricsConfig.recordInvocation("evaluate", chaincode, "failure");

            log.error("Unexpected error in evaluateTransaction: chaincode={} function={}",
                    chaincode, function, ex);
            throw new BlockchainServiceException(
                    "Unexpected error querying " + function + ": " + ex.getMessage(), ex);
        }
    }

    @SuppressWarnings("unused")
    private byte[] evaluateTransactionFallback(String chaincode, String function, String[] args, Throwable throwable) {
        log.warn("Circuit breaker OPEN or bulkhead FULL for evaluateTransaction: chaincode={} function={} args={} reason={}",
                chaincode, function, Arrays.toString(args), throwable.getMessage());

        metricsConfig.recordInvocation("evaluate", chaincode, "circuit_open");
        metricsConfig.recordCircuitBreakerEvent("open", "fallback_triggered");
        return new byte[0];
    }

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

    private Contract resolveContract(String chaincode) {
        return switch (chaincode) {
            case "transaction-cc" -> transactionContract;
            case "audit-cc" -> auditContract;
            default -> throw new BlockchainServiceException("Unknown chaincode identifier: " + chaincode);
        };
    }

    public record FabricSubmitResult(
            byte[] result,
            String transactionId,
            String blockNumber,
            boolean committed
    ) {
        public static FabricSubmitResult pending() {
            return new FabricSubmitResult(new byte[0], null, null, false);
        }
    }

    public static class BlockchainServiceException extends RuntimeException {

        public BlockchainServiceException(String message) {
            super(message);
        }

        public BlockchainServiceException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
