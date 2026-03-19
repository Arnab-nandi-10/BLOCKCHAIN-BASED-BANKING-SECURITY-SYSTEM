package com.bbss.blockchain.dto;

import java.time.LocalDateTime;

/**
 * On-chain transaction record returned when querying the Hyperledger Fabric ledger.
 *
 * @param transactionId  business transaction ID
 * @param blockchainTxId the Hyperledger Fabric transaction hash
 * @param blockNumber    block number where the transaction was committed
 * @param status         on-chain status at the time of commit
 * @param timestamp      block commit timestamp
 */
public record BlockchainTransactionResponse(
        String transactionId,
        String blockchainTxId,
        String blockNumber,
        String status,
        LocalDateTime timestamp
) {}
