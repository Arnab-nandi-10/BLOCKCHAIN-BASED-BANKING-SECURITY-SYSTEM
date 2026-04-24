package com.bbss.blockchain.dto;

/**
 * Response returned by the blockchain-service after anchoring an audit record
 * to the Hyperledger Fabric ledger.
 *
 * @param auditId        echoed audit identifier for correlation
 * @param blockchainTxId immutable blockchain transaction hash (null when Fabric is disabled)
 * @param blockNumber    block number on the distributed ledger (null when Fabric is disabled)
 * @param verificationStatus latest hash verification outcome
 * @param success        true if the record was successfully committed (or simulated)
 */
public record BlockchainAuditResponse(
        String  auditId,
        String  blockchainTxId,
        String  blockNumber,
        String  verificationStatus,
        boolean success
) {}
