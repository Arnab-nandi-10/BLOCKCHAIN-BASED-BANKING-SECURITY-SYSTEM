package com.bbss.blockchain.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Request payload received from the transaction-service when submitting
 * a transaction to the Hyperledger Fabric ledger.
 *
 * @param transactionId business transaction ID
 * @param tenantId      tenant identifier
 * @param fromAccount   source account
 * @param toAccount     destination account
 * @param amount        transaction amount
 * @param currency      ISO 4217 currency code
 * @param type          transaction type name (TRANSFER, PAYMENT, etc.)
 * @param status        current transaction status at the time of submission
 * @param fraudScore    fraud score captured before the blockchain write
 * @param riskLevel     fraud risk level captured before the blockchain write
 * @param decision      business decision captured before the blockchain write
 * @param decisionReason explanation for the decision
 * @param timestamp     original transaction creation timestamp
 * @param ipAddress     originating IP address for audit correlation
 * @param metadata      optional metadata used for explainability
 */
public record BlockchainSubmitRequest(
        String transactionId,
        String tenantId,
        String fromAccount,
        String toAccount,
        BigDecimal amount,
        String currency,
        String type,
        String status,
        Double fraudScore,
        String riskLevel,
        String decision,
        String decisionReason,
        LocalDateTime timestamp,
        String ipAddress,
        Map<String, String> metadata
) {}
