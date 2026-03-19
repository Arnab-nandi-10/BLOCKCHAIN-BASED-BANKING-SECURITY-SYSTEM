package com.bbss.blockchain.dto;

/**
 * Response returned after submitting a transaction to the Hyperledger Fabric ledger.
 *
 * @param transactionId  echoed business transaction ID
 * @param blockchainTxId the Hyperledger Fabric transaction hash (null on failure)
 * @param blockNumber    the block number where the transaction was committed (null on failure)
 * @param success        true when the ledger write succeeded
 * @param message        human-readable result message (error description on failure)
 */
public record BlockchainSubmitResponse(
        String transactionId,
        String blockchainTxId,
        String blockNumber,
        boolean success,
        String message
) {}
