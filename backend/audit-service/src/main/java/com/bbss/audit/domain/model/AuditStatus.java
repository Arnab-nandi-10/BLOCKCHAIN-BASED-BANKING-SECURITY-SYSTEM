package com.bbss.audit.domain.model;

/**
 * Lifecycle states of a blockchain-anchored audit entry.
 *
 * <ul>
 *   <li>{@link #PENDING}   – Record has been persisted locally but not yet
 *       confirmed on the blockchain ledger.  This is the initial state for
 *       every newly created {@code AuditEntry}.</li>
 *   <li>{@link #COMMITTED} – The blockchain-service has returned a valid
 *       transaction ID and block number; the entry is immutably anchored.</li>
 *   <li>{@link #FAILED}    – The blockchain commit attempt failed (e.g. the
 *       blockchain-service was unavailable).  The scheduled retry job will
 *       attempt to re-submit FAILED entries.</li>
 * </ul>
 */
public enum AuditStatus {

    /** Persisted locally; awaiting blockchain confirmation. */
    PENDING,

    /** Successfully anchored on the distributed ledger. */
    COMMITTED,

    /** Blockchain commit failed; eligible for retry. */
    FAILED
}
