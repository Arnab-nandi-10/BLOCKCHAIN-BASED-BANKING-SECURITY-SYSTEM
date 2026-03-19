package com.bbss.shared.events;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Kafka domain event emitted by the fraud detection engine whenever a
 * transaction is evaluated and a notable risk level is determined.
 *
 * <p>Producers: {@code fraud-detection-service} (internal), {@code transaction-service}
 * <br>Consumers: {@code audit-service}, {@code notification-service},
 *                {@code transaction-service} (to update transaction status),
 *                {@code compliance-service}
 *
 * <p>Topic naming convention: {@code bbss.fraud.alerts}
 *
 * <p>Serialisation: Jackson (JSON) via Spring Kafka's
 * {@code JsonSerializer} / {@code JsonDeserializer}.  {@link Serializable} is
 * implemented for in-memory and test broker scenarios.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FraudAlertEvent implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // ── Identity ──────────────────────────────────────────────────────────────

    /**
     * Globally unique event identifier (UUID v4).
     * Consumers must use this for idempotent processing and deduplication.
     */
    private String eventId;

    /**
     * Identifier of the transaction that triggered this alert.
     * Matches {@link TransactionEvent#getTransactionId()}.
     */
    private String transactionId;

    /**
     * Tenant that owns the flagged transaction.
     */
    private String tenantId;

    // ── Fraud assessment ──────────────────────────────────────────────────────

    /**
     * Normalised fraud probability score in the range {@code [0.0, 1.0]}.
     * <ul>
     *   <li>0.00 – 0.29 → LOW</li>
     *   <li>0.30 – 0.59 → MEDIUM</li>
     *   <li>0.60 – 0.84 → HIGH</li>
     *   <li>0.85 – 1.00 → CRITICAL</li>
     * </ul>
     */
    private double fraudScore;

    /**
     * Categorical risk band derived from {@link #fraudScore}.
     * One of: {@code LOW}, {@code MEDIUM}, {@code HIGH}, {@code CRITICAL}.
     */
    private String riskLevel;

    /**
     * Ordered list of rule identifiers or human-readable descriptions that
     * contributed to the alert.
     *
     * <p>Examples:
     * <ul>
     *   <li>"VELOCITY_LIMIT_EXCEEDED"</li>
     *   <li>"UNUSUAL_GEOGRAPHIC_LOCATION"</li>
     *   <li>"KNOWN_FRAUD_PATTERN_03"</li>
     * </ul>
     */
    private List<String> triggeredRules;

    /**
     * Machine-generated recommendation for downstream consumers.
     * Typical values: {@code "BLOCK"}, {@code "MANUAL_REVIEW"}, {@code "ALLOW"}.
     */
    private String recommendation;

    // ── Metadata ──────────────────────────────────────────────────────────────

    /**
     * Server-side instant at which the fraud engine produced this assessment.
     */
    private LocalDateTime detectedAt;
}
