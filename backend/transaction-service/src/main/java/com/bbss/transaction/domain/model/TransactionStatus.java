package com.bbss.transaction.domain.model;

/**
 * Represents the lifecycle status of a transaction through the
 * fraud-check and blockchain submission pipeline.
 */
public enum TransactionStatus {

    /**
     * Transaction has been received and persisted.
     */
    SUBMITTED,

    /**
     * Transaction is awaiting fraud evaluation.
     */
    PENDING_FRAUD_CHECK,

    /**
     * Transaction passed fraud checks. Ledger anchoring is tracked separately by {@link LedgerStatus}.
     */
    VERIFIED,

    /**
     * Transaction has been flagged for manual fraud review (score 0.5–0.79).
     */
    FRAUD_HOLD,

    /**
     * Transaction has been automatically blocked by the fraud engine (score >= 0.8).
     */
    BLOCKED,

    /**
     * Transaction has been fully processed and settled.
     */
    COMPLETED,

    /**
     * Transaction processing failed (blockchain error or unrecoverable state).
     */
    FAILED
}
