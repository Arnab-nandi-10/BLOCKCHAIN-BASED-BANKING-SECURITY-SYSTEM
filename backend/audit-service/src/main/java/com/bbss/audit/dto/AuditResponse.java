package com.bbss.audit.dto;

import com.bbss.audit.domain.model.AuditStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * REST response DTO for a single {@link com.bbss.audit.domain.model.AuditEntry}.
 *
 * <p>Fields with {@code null} values are omitted from the JSON output via
 * {@link JsonInclude.Include#NON_NULL} to keep responses compact.
 */
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AuditResponse {

    /**
     * Business-level audit identifier, sourced from the originating event's
     * {@code eventId}.  Stable across retries.
     */
    private final String auditId;

    /** Tenant that owns this audit record. */
    private final String tenantId;

    /**
     * Domain category of the affected entity.
     * E.g. {@code "TRANSACTION"}, {@code "USER"}, {@code "TENANT"}.
     */
    private final String entityType;

    /** Primary key or external identifier of the affected entity instance. */
    private final String entityId;

    /**
     * Verb describing the audited event.
     * E.g. {@code "TRANSACTION_VERIFIED"}, {@code "FRAUD_DETECTED"},
     *      {@code "USER_LOGIN"}, {@code "TENANT_PROVISIONED"}.
     */
    private final String action;

    /** Identifier of the user or service that triggered the event. */
    private final String actorId;

    /**
     * Actor category: {@code "USER"}, {@code "SYSTEM"}, or {@code "API"}.
     */
    private final String actorType;

    /** Originating client IP address; {@code null} for system-generated events. */
    private final String ipAddress;

    /**
     * Full JSON snapshot of the originating event payload.
     * May contain PII; callers must apply appropriate masking for non-privileged roles.
     */
    private final String payload;

    /** Blockchain transaction hash; {@code null} while status is PENDING. */
    private final String blockchainTxId;

    /** Ledger block number; {@code null} while status is PENDING. */
    private final String blockNumber;

    /** Latest ledger integrity verification outcome when available. */
    private final String verificationStatus;

    /**
     * Current lifecycle status of the audit record.
     *
     * @see AuditStatus
     */
    private final AuditStatus status;

    /** End-to-end request correlation identifier. */
    private final String correlationId;

    /** Instant at which the auditable event originally occurred. */
    private final LocalDateTime occurredAt;
}
