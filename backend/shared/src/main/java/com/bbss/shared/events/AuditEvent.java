package com.bbss.shared.events;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Kafka domain event emitted whenever an auditable action occurs within the
 * BBSS platform.
 *
 * <p>Every significant state change – user login, transaction creation,
 * tenant provisioning, role modification – should produce an {@code AuditEvent}
 * so that the {@code audit-service} can build an immutable, append-only audit
 * trail.
 *
 * <p>Producers: every BBSS microservice
 * <br>Consumers: {@code audit-service} exclusively
 *
 * <p>Topic naming convention: {@code bbss.audit.events}
 *
 * <p>Serialisation: Jackson (JSON) via Spring Kafka's
 * {@code JsonSerializer} / {@code JsonDeserializer}.  {@link Serializable} is
 * implemented for in-memory and test broker scenarios.
 *
 * <p><strong>PII / data sensitivity</strong>: the {@link #payload} field may
 * contain personally identifiable information.  The audit-service must store
 * it encrypted at rest and apply appropriate retention policies.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditEvent implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // ── Identity ──────────────────────────────────────────────────────────────

    /**
     * Globally unique event identifier (UUID v4).
     * Consumers deduplicate on this key for exactly-once audit record creation.
     */
    private String eventId;

    /**
     * Tenant in whose context the action was performed.
     * Used by the audit-service to partition records into tenant-specific stores.
     */
    private String tenantId;

    // ── What happened ─────────────────────────────────────────────────────────

    /**
     * Domain type of the entity that was affected (e.g. "TRANSACTION", "USER",
     * "TENANT", "ACCOUNT", "ROLE").
     */
    private String entityType;

    /**
     * Identifier of the specific entity instance (primary key or external id).
     */
    private String entityId;

    /**
     * Verb describing the action performed (e.g. "CREATE", "UPDATE", "DELETE",
     * "LOGIN", "LOGOUT", "APPROVE", "REJECT").
     */
    private String action;

    // ── Who did it ────────────────────────────────────────────────────────────

    /**
     * Identifier of the principal that performed the action.
     * For human users: the user UUID.
     * For service accounts: the service name / client_id.
     */
    private String actorId;

    /**
     * Category of the actor: {@code "USER"}, {@code "SERVICE"}, or {@code "SYSTEM"}.
     */
    private String actorType;

    /**
     * IPv4 or IPv6 address of the originating request, extracted from
     * {@code X-Forwarded-For} or {@code RemoteAddr}.
     * {@code null} for internally generated events that have no HTTP origin.
     */
    private String ipAddress;

    // ── Context ───────────────────────────────────────────────────────────────

    /**
     * Arbitrary payload carrying the before/after state or relevant metadata
     * for the audited action.
     *
     * <p>This field is typed as {@link Object} so that producers can pass any
     * serialisable POJO; the Kafka {@code JsonSerializer} will convert it to a
     * JSON node, and the audit-service will persist it as a JSON column.
     *
     * <p><strong>Contract</strong>: producers must ensure that the object is
     * Jackson-serialisable (no circular references, no un-registered types).
     */
    private Object payload;

    /**
     * Server-side instant at which the event was raised.
     */
    private LocalDateTime occurredAt;

    /**
     * Optional correlation identifier propagated from the originating HTTP
     * request (value of the {@code X-Correlation-Id} header).
     * Enables end-to-end request tracing across service boundaries.
     */
    private String correlationId;
}
