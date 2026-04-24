package com.bbss.blockchain.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Local PostgreSQL cache of blockchain records committed to the Hyperledger Fabric ledger.
 *
 * <p>This table serves as a fast local index so that individual transaction queries
 * can be resolved without round-tripping to the Fabric peer for every request.</p>
 */
@Entity
@Table(
    name = "blockchain_records",
    indexes = {
        @Index(name = "idx_blockchain_records_transaction_id", columnList = "transaction_id", unique = true),
        @Index(name = "idx_blockchain_records_tenant_id",      columnList = "tenant_id"),
        @Index(name = "idx_blockchain_records_created_at",     columnList = "created_at")
    }
)
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BlockchainRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /** Business transaction ID sourced from the transaction-service. */
    @Column(name = "transaction_id", unique = true, nullable = false, length = 64)
    private String transactionId;

    /** Tenant identifier. */
    @Column(name = "tenant_id", nullable = false, length = 64)
    private String tenantId;

    /** Hyperledger Fabric transaction hash returned by the peer after commit. */
    @Column(name = "blockchain_tx_id", length = 128)
    private String blockchainTxId;

    /** Block number in which the transaction was committed on the channel. */
    @Column(name = "block_number", length = 64)
    private String blockNumber;

    /** Chaincode ID / name used to submit the transaction. */
    @Column(name = "chaincode_id", length = 128)
    private String chaincodeId;

    /**
     * JSON-serialised chaincode input used for audit, replay and retry purposes.
     */
    @Column(name = "payload", columnDefinition = "TEXT")
    private String payload;

    /** On-chain status (mirrors the transaction status at the time of ledger write). */
    @Column(name = "status", length = 32)
    private String status;

    @Column(name = "ledger_status", nullable = false, length = 32)
    private String ledgerStatus;

    @Column(name = "verification_status", nullable = false, length = 32)
    private String verificationStatus;

    @Column(name = "payload_hash", length = 128)
    private String payloadHash;

    @Column(name = "record_hash", length = 128)
    private String recordHash;

    @Column(name = "previous_hash", length = 128)
    private String previousHash;

    @Column(name = "last_error", length = 1024)
    private String lastError;

    @Column(name = "retry_count", nullable = false)
    @Builder.Default
    private int retryCount = 0;

    @Column(name = "next_retry_at")
    private LocalDateTime nextRetryAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
