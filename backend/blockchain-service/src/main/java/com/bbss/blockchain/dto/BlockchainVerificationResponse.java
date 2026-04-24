package com.bbss.blockchain.dto;

/**
 * Response returned by integrity verification endpoints.
 */
public record BlockchainVerificationResponse(
        String recordId,
        String verificationStatus,
        boolean valid,
        String payloadHash,
        String recomputedPayloadHash,
        String recordHash,
        String recomputedRecordHash,
        String previousHash,
        String verifiedAt
) {}
