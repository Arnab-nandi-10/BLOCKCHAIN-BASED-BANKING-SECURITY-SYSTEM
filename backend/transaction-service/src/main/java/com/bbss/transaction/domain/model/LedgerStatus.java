package com.bbss.transaction.domain.model;

/**
 * Tracks the Fabric anchoring lifecycle independently from the business transaction status.
 */
public enum LedgerStatus {
    NOT_REQUESTED,
    PENDING_LEDGER,
    COMMITTED,
    FAILED_LEDGER
}
