package com.bbss.blockchain.dto;

/**
 * Response returned after submitting a transaction to the Hyperledger Fabric ledger.
 *
 * @param transactionId  echoed business transaction ID
 * @param blockchainTxId the Hyperledger Fabric transaction hash (null on failure)
 * @param blockNumber    the block number where the transaction was committed (null on failure)
 * @param ledgerStatus   current ledger state (COMMITTED / PENDING_LEDGER)
 * @param verificationStatus latest hash verification state
 * @param payloadHash    SHA-256 digest of the sanitised payload
 * @param recordHash     SHA-256 digest of the anchored record envelope
 * @param previousHash   optional link to the previous record hash
 * @param success        true when the ledger write succeeded
 * @param message        human-readable result message (error description on failure)
 */
public record BlockchainSubmitResponse(
        String transactionId,
        String blockchainTxId,
        String blockNumber,
        String ledgerStatus,
        String verificationStatus,
        String payloadHash,
        String recordHash,
        String previousHash,
        boolean success,
        String message
) {}
