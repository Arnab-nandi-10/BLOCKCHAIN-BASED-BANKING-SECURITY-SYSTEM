package com.bbss.transaction.dto;

import com.bbss.transaction.domain.model.TransactionStatus;
import com.bbss.transaction.domain.model.TransactionType;
import com.bbss.transaction.domain.model.LedgerStatus;
import com.bbss.transaction.domain.model.VerificationStatus;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Response DTO representing the public view of a transaction.
 */
@Getter
@Builder
public class TransactionResponse {

    /** Business transaction identifier (UUID string). */
    private final String transactionId;

    /** Tenant that owns this transaction. */
    private final String tenantId;

    /** Source account identifier. */
    private final String fromAccount;

    /** Destination account identifier. */
    private final String toAccount;

    /** Transaction amount with up to 4 decimal places. */
    private final BigDecimal amount;

    /** ISO 4217 currency code. */
    private final String currency;

    /** Transaction category. */
    private final TransactionType type;

    /** Current lifecycle status. */
    private final TransactionStatus status;

    /** Ledger anchoring state, independent from the business transaction status. */
    private final LedgerStatus ledgerStatus;

    /** Latest integrity verification result returned by blockchain-service. */
    private final VerificationStatus verificationStatus;

    /** Hyperledger Fabric transaction hash (null until ledger write succeeds). */
    private final String blockchainTxId;

    /** Block number on the Hyperledger Fabric channel (null until ledger write succeeds). */
    private final String blockNumber;

    /** Fraud probability score in [0.0, 1.0]. -1 means fraud service was unavailable. */
    private final double fraudScore;

    /** Human-readable fraud risk level (LOW / MEDIUM / HIGH / UNKNOWN). */
    private final String fraudRiskLevel;

    /** Explicit fraud engine decision (ALLOW / MONITOR / HOLD / BLOCK). */
    private final String fraudDecision;

    /** Operational recommendation returned by the fraud engine. */
    private final String fraudRecommendation;

    /** Whether manual review is required for this transaction. */
    private final boolean reviewRequired;

    /** Rule identifiers that contributed to the fraud outcome. */
    private final List<String> triggeredRules;

    /** Human-readable explanations returned by the fraud engine. */
    private final List<String> explanations;

    /** Latest payload hash returned by blockchain verification or commit flow. */
    private final String payloadHash;

    /** Latest record hash returned by blockchain verification or commit flow. */
    private final String recordHash;

    /** Previous hash in the tenant chain when available. */
    private final String previousHash;

    /** Request correlation identifier used for recovery and tracing. */
    private final String correlationId;

    /** Timestamp when the transaction was first submitted. */
    private final LocalDateTime createdAt;

    /** Timestamp when the transaction reached a terminal status. */
    private final LocalDateTime completedAt;
}
