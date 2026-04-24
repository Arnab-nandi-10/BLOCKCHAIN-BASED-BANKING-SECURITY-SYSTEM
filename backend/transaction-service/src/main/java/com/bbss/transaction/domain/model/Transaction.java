package com.bbss.transaction.domain.model;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(
    name = "transactions",
    indexes = {
        @Index(name = "idx_transactions_transaction_id", columnList = "transaction_id", unique = true),
        @Index(name = "idx_transactions_tenant_id", columnList = "tenant_id"),
        @Index(name = "idx_transactions_from_account", columnList = "from_account"),
        @Index(name = "idx_transactions_status", columnList = "status")
    }
)
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /**
     * Business transaction ID — UUID string, generated on submit.
     */
    @Column(name = "transaction_id", unique = true, nullable = false, length = 64)
    private String transactionId;

    @Column(name = "tenant_id", nullable = false, length = 64)
    private String tenantId;

    @Column(name = "from_account", nullable = false, length = 128)
    private String fromAccount;

    @Column(name = "to_account", nullable = false, length = 128)
    private String toAccount;

    @Column(name = "amount", precision = 19, scale = 4, nullable = false)
    private BigDecimal amount;

    /**
     * ISO 4217 currency code, e.g. "USD".
     */
    @Column(name = "currency", length = 3, nullable = false)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 32)
    private TransactionType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private TransactionStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "ledger_status", nullable = false, length = 32)
    private LedgerStatus ledgerStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "verification_status", nullable = false, length = 32)
    private VerificationStatus verificationStatus;

    /**
     * Hyperledger Fabric transaction hash, set after ledger write.
     */
    @Column(name = "blockchain_tx_id", length = 128)
    private String blockchainTxId;

    @Column(name = "block_number", length = 64)
    private String blockNumber;

    /**
     * Fraud score returned by the fraud detection service (0.0 - 1.0).
     * A value of -1 indicates the fraud service was unavailable.
     */
    @Column(name = "fraud_score")
    private double fraudScore;

    @Column(name = "fraud_risk_level", length = 32)
    private String fraudRiskLevel;

    @Column(name = "fraud_decision", length = 16)
    private String fraudDecision;

    @Column(name = "fraud_recommendation", length = 128)
    private String fraudRecommendation;

    @Column(name = "review_required", nullable = false)
    @Builder.Default
    private boolean reviewRequired = false;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
        name = "transaction_triggered_rules",
        joinColumns = @JoinColumn(name = "transaction_id")
    )
    @Column(name = "rule_value", length = 255)
    @Builder.Default
    private List<String> triggeredRules = new ArrayList<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
        name = "transaction_explanations",
        joinColumns = @JoinColumn(name = "transaction_id")
    )
    @Column(name = "explanation", length = 512)
    @Builder.Default
    private List<String> explanations = new ArrayList<>();

    @Column(name = "payload_hash", length = 128)
    private String payloadHash;

    @Column(name = "record_hash", length = 128)
    private String recordHash;

    @Column(name = "previous_hash", length = 128)
    private String previousHash;

    @Column(name = "rejection_reason", length = 512)
    private String rejectionReason;

    @Column(name = "correlation_id", length = 64)
    private String correlationId;

    /**
     * IP address of the transaction submitter.
     */
    @Column(name = "ip_address", length = 64)
    private String ipAddress;

    @Column(name = "user_agent", length = 512)
    private String userAgent;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
        name = "transaction_metadata",
        joinColumns = @JoinColumn(name = "transaction_id")
    )
    @MapKeyColumn(name = "meta_key")
    @Column(name = "meta_value", length = 1024)
    @Builder.Default
    private Map<String, String> metadata = new HashMap<>();

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;
}
