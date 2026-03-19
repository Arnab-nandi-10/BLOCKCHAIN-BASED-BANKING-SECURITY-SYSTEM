package com.bbss.transaction.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Feign client for the blockchain-service microservice.
 * The base URL is resolved from application properties and may be overridden
 * via the {@code BLOCKCHAIN_SERVICE_URL} environment variable.
 */
@FeignClient(name = "blockchain-service", url = "${services.blockchain-service.url}")
public interface BlockchainServiceClient {

    /**
     * Submit a verified transaction to the Hyperledger Fabric ledger.
     *
     * @param request the transaction payload to persist on-chain
     * @return blockchain submission result containing the tx hash and block number
     */
    @PostMapping("/api/v1/blockchain/transactions")
    ApiResponse<BlockchainSubmitResponse> submitTransaction(@RequestBody BlockchainSubmitRequest request);

    /**
     * Retrieve an on-chain transaction record by its business ID.
     *
     * @param txId the business transaction ID
     * @return on-chain transaction details
     */
    @GetMapping("/api/v1/blockchain/transactions/{txId}")
    ApiResponse<BlockchainTransactionResponse> getTransaction(@PathVariable("txId") String txId);

    // -------------------------------------------------------------------------
    // Request / Response records
    // -------------------------------------------------------------------------

    /**
     * Payload sent to the blockchain-service for on-chain submission.
     *
     * @param transactionId business transaction ID
     * @param tenantId      tenant identifier
     * @param fromAccount   source account
     * @param toAccount     destination account
     * @param amount        transaction amount
     * @param currency      ISO 4217 currency code
     * @param type          transaction type name
     * @param status        current transaction status
     * @param timestamp     transaction creation timestamp
     */
    record BlockchainSubmitRequest(
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

    /**
     * Result of submitting a transaction to the Hyperledger Fabric ledger.
     *
     * @param transactionId  echoed business transaction ID
     * @param blockchainTxId the Hyperledger Fabric transaction hash
     * @param blockNumber    the block number where the transaction was committed
     * @param success        true when the submission succeeded
     * @param message        informational message (error description on failure)
     */
    record BlockchainSubmitResponse(
            String transactionId,
            String blockchainTxId,
            String blockNumber,
            boolean success,
            String message
    ) {}

    /**
     * On-chain transaction record returned by the blockchain-service.
     *
     * @param transactionId  business transaction ID
     * @param blockchainTxId the Hyperledger Fabric transaction hash
     * @param blockNumber    block number where the transaction was committed
     * @param status         on-chain status
     * @param timestamp      block commit timestamp
     */
    record BlockchainTransactionResponse(
            String transactionId,
            String blockchainTxId,
            String blockNumber,
            String status,
            LocalDateTime timestamp
    ) {}

    /**
     * Generic wrapper matching the shared ApiResponse envelope returned by all services.
     */
    record ApiResponse<T>(
            boolean success,
            String message,
            T data
    ) {}
}
