package com.bbss.blockchain.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

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
 * @param timestamp     original transaction creation timestamp
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
        LocalDateTime timestamp
) {}
