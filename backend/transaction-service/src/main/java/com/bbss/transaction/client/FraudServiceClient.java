package com.bbss.transaction.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Feign client for the Python fraud-detection microservice.
 * The base URL is resolved from application properties and may be overridden
 * via the {@code FRAUD_SERVICE_URL} environment variable.
 */
@FeignClient(name = "fraud-detection", url = "${services.fraud-detection.url}")
public interface FraudServiceClient {

    /**
     * Request a fraud risk score for a submitted transaction.
     *
     * @param request the fraud scoring request payload
     * @return fraud score and risk classification from the fraud engine
     */
    @PostMapping("/api/v1/fraud/score")
    FraudScoreResponse scoreFraud(@RequestBody FraudScoreRequest request);

    // -------------------------------------------------------------------------
    // Request / Response records (defined here to keep the client self-contained)
    // -------------------------------------------------------------------------

    /**
     * Payload sent to the fraud-detection service for scoring.
     *
     * @param transactionId   business transaction ID
     * @param tenantId        tenant identifier
     * @param fromAccount     source account
     * @param toAccount       destination account
     * @param amount          transaction amount
     * @param currency        ISO 4217 currency code
     * @param transactionType transaction type name (TRANSFER, PAYMENT, etc.)
     * @param ipAddress       submitter IP address
     * @param metadata        arbitrary key-value metadata
     */
    record FraudScoreRequest(
            String transactionId,
            String tenantId,
            String fromAccount,
            String toAccount,
            BigDecimal amount,
            String currency,
            String transactionType,
            String ipAddress,
            Map<String, String> metadata
    ) {}

    /**
     * Fraud scoring result returned by the fraud-detection service.
     *
     * @param transactionId    echoed business transaction ID
     * @param score            fraud probability score in the range [0.0, 1.0]
     * @param riskLevel        human-readable risk classification (LOW / MEDIUM / HIGH)
     * @param triggeredRules   list of rule identifiers that fired
     * @param recommendation   plain-text recommendation from the fraud engine
     * @param shouldBlock      true when the engine recommends an immediate block
     */
    record FraudScoreResponse(
            String transactionId,
            double score,
            String riskLevel,
            List<String> triggeredRules,
            String recommendation,
            boolean shouldBlock
    ) {}
}
