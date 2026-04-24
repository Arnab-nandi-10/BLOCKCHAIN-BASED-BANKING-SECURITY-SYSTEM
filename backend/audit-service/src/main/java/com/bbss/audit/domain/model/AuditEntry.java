package com.bbss.audit.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * Persistent record of a single auditable platform event.
 *
 * <p>Every significant state change produced by any Civic Savings service is captured as
 * an {@code AuditEntry} and anchored on the blockchain ledger.  The combination
 * of the relational database record and the blockchain transaction ID forms an
 * immutable, tamper-evident log.
 *
 * <p>Lifecycle:
 * <ol>
 *   <li>Record created with {@link AuditStatus#PENDING}.</li>
 *   <li>Asynchronous job calls {@code blockchain-service}; on success the record
 *       is updated to {@link AuditStatus#COMMITTED} with the ledger coordinates.</li>
 *   <li>On transient failure the record remains (or transitions to)
 *       {@link AuditStatus#FAILED} and the retry scheduler re-processes it.</li>
 * </ol>
 */
@Entity
@Table(
    name = "audit_entries",
    indexes = {
        @Index(name = "idx_audit_tenant",         columnList = "tenant_id"),
        @Index(name = "idx_audit_entity",          columnList = "entity_type, entity_id"),
        @Index(name = "idx_audit_id",              columnList = "audit_id"),
        @Index(name = "idx_audit_blockchain_tx",   columnList = "blockchain_tx_id"),
        @Index(name = "idx_audit_status",          columnList = "status"),
        @Index(name = "idx_audit_occurred_at",     columnList = "occurred_at"),
        @Index(name = "idx_audit_tenant_occurred", columnList = "tenant_id, occurred_at")
    }
)
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditEntry {

    // ── Identity ──────────────────────────────────────────────────────────────

    /** Internal surrogate key; never exposed in API responses. */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * Business-level unique identifier, sourced from the originating event's
     * {@code eventId}.  Enables idempotent ingestion: a second attempt to
     * record the same event is a no-op.
     */
    @Column(name = "audit_id", unique = true, nullable = false, length = 100)
    private String auditId;

    // ── Tenant ────────────────────────────────────────────────────────────────

    @Column(name = "tenant_id", nullable = false, length = 100)
    private String tenantId;

    // ── What happened ─────────────────────────────────────────────────────────

    /**
     * Domain category of the affected entity.
     * E.g. {@code "TRANSACTION"}, {@code "USER"}, {@code "TENANT"}.
     */
    @Column(name = "entity_type", nullable = false, length = 50)
    private String entityType;

    /** Primary key or external identifier of the affected entity instance. */
    @Column(name = "entity_id", nullable = false, length = 255)
    private String entityId;

    /**
     * Verb describing the event.
     * E.g. {@code "TRANSACTION_SUBMITTED"}, {@code "FRAUD_DETECTED"},
     *      {@code "USER_LOGIN"}, {@code "TENANT_PROVISIONED"}.
     */
    @Column(name = "action", nullable = false, length = 100)
    private String action;

    // ── Who did it ────────────────────────────────────────────────────────────

    /** UUID of the user or name of the service that triggered the event. */
    @Column(name = "actor_id", nullable = false, length = 255)
    private String actorId;

    /**
     * Actor category.
     * One of: {@code "USER"}, {@code "SYSTEM"}, {@code "API"}.
     */
    @Column(name = "actor_type", nullable = false, length = 20)
    private String actorType;

    /** Originating IPv4/IPv6 address; {@code null} for system-generated events. */
    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    // ── Payload ───────────────────────────────────────────────────────────────

    /** Full JSON snapshot of the originating event; stored verbatim for forensics. */
    @Column(name = "payload", columnDefinition = "TEXT")
    private String payload;

    // ── Blockchain coordinates ────────────────────────────────────────────────

    /** Blockchain transaction hash returned by {@code blockchain-service} after commit. */
    @Column(name = "blockchain_tx_id", length = 255)
    private String blockchainTxId;

    /** Block number on the ledger where this audit record was anchored. */
    @Column(name = "block_number", length = 50)
    private String blockNumber;

    /** Latest integrity verification result returned by blockchain-service. */
    @Column(name = "verification_status", length = 32)
    private String verificationStatus;

    // ── Status ────────────────────────────────────────────────────────────────

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AuditStatus status;

    // ── Tracing ───────────────────────────────────────────────────────────────

    /** End-to-end request correlation identifier from {@code X-Correlation-Id}. */
    @Column(name = "correlation_id", length = 100)
    private String correlationId;

    // ── Timestamps ────────────────────────────────────────────────────────────

    /** Instant at which the audit-worthy event originally occurred. */
    @CreatedDate
    @Column(name = "occurred_at", nullable = false, updatable = false)
    private LocalDateTime occurredAt;

    /** Instant of the last status update (e.g. PENDING → COMMITTED). */
    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
