package com.bbss.transaction.domain.model;

/**
 * Tracks the latest integrity verification result for the on-chain record.
 */
public enum VerificationStatus {
    NOT_VERIFIED,
    VERIFIED,
    HASH_MISMATCH,
    UNAVAILABLE
}
