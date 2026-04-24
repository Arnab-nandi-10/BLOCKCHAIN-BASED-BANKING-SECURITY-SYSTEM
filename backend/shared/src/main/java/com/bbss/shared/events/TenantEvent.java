package com.bbss.shared.events;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Kafka domain event published when the lifecycle state of a tenant changes
 * inside the Civic Savings platform.
 *
 * <p>Producers: {@code tenant-service}
 * <br>Consumers: every microservice that must react to tenant provisioning or
 *                deprovisioning: {@code auth-service} (create/revoke OAuth
 *                clients), {@code transaction-service} (enable/disable ledger
 *                partitions), {@code audit-service} (initialise audit schema),
 *                {@code notification-service} (send welcome / farewell emails).
 *
 * <p>Topic naming convention: {@code bbss.tenants.events}
 *
 * <p>Serialisation: Jackson (JSON) via Spring Kafka's
 * {@code JsonSerializer} / {@code JsonDeserializer}.  {@link Serializable} is
 * implemented for in-memory and test broker scenarios.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantEvent implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // ── Identity ──────────────────────────────────────────────────────────────

    /**
     * Globally unique event identifier (UUID v4).
     * Consumers must deduplicate on this key to guarantee idempotent processing.
     */
    private String eventId;

    /**
     * Canonical identifier of the tenant (typically a UUID or a slug such as
     * {@code "acme-bank"}).
     */
    private String tenantId;

    // ── Tenant metadata ───────────────────────────────────────────────────────

    /**
     * Human-readable display name of the tenant organisation
     * (e.g. "Acme National Bank").
     */
    private String tenantName;

    /**
     * Subscription tier / service plan for the tenant.
     * Examples: {@code "STARTER"}, {@code "PROFESSIONAL"}, {@code "ENTERPRISE"}.
     */
    private String planType;

    /**
     * Email address of the tenant's primary administrator account.
     * The auth-service uses this to bootstrap the initial super-admin user for
     * the tenant.
     */
    private String adminEmail;

    // ── Event type ────────────────────────────────────────────────────────────

    /**
     * Action that triggered this event.
     *
     * <ul>
     *   <li>{@code PROVISIONED} – tenant has been successfully created and is
     *       ready to onboard users</li>
     *   <li>{@code SUSPENDED}   – tenant account has been temporarily disabled
     *       (e.g. overdue payment, compliance hold)</li>
     *   <li>{@code DELETED}     – tenant and all associated data are scheduled
     *       for permanent removal</li>
     * </ul>
     */
    private String action;

    // ── Metadata ──────────────────────────────────────────────────────────────

    /**
     * Server-side instant at which this event was generated.
     */
    private LocalDateTime timestamp;
}
