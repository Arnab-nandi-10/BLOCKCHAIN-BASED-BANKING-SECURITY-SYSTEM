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
 * Local persistence for simulated audit records when Fabric is disabled.
 */
@Entity
@Table(
    name = "simulated_audit_records",
    indexes = {
        @Index(name = "idx_sim_audit_records_audit_id", columnList = "audit_id", unique = true),
        @Index(name = "idx_sim_audit_records_tenant_id", columnList = "tenant_id"),
        @Index(name = "idx_sim_audit_records_created_at", columnList = "created_at")
    }
)
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SimulatedAuditRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "audit_id", unique = true, nullable = false, length = 100)
    private String auditId;

    @Column(name = "tenant_id", nullable = false, length = 64)
    private String tenantId;

    @Column(name = "blockchain_tx_id", length = 128)
    private String blockchainTxId;

    @Column(name = "block_number", length = 64)
    private String blockNumber;

    @Column(name = "payload_hash", length = 128)
    private String payloadHash;

    @Column(name = "record_hash", length = 128)
    private String recordHash;

    @Column(name = "previous_hash", length = 128)
    private String previousHash;

    @Column(name = "verification_status", nullable = false, length = 32)
    private String verificationStatus;

    @Column(name = "record_json", columnDefinition = "TEXT", nullable = false)
    private String recordJson;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
