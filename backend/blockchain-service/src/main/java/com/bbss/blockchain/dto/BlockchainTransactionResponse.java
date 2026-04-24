package com.bbss.blockchain.dto;

import java.time.LocalDateTime;

/**
 * On-chain transaction record returned when querying the Hyperledger Fabric ledger.
 *
 * @param transactionId  business transaction ID
 * @param blockchainTxId the Hyperledger Fabric transaction hash
 * @param blockNumber    block number where the transaction was committed
 * @param ledgerStatus   current ledger state
 * @param verificationStatus latest verification outcome
 * @param payloadHash    SHA-256 digest of the sanitised payload
 * @param recordHash     SHA-256 digest of the stored record
 * @param previousHash   optional link to the previous record hash
 * @param status         on-chain status at the time of commit
 * @param timestamp      block commit timestamp
 */
public record BlockchainTransactionResponse(
        String transactionId,
        String blockchainTxId,
        String blockNumber,
        String ledgerStatus,
        String verificationStatus,
        String payloadHash,
        String recordHash,
        String previousHash,
        String status,
        LocalDateTime timestamp
) {}
