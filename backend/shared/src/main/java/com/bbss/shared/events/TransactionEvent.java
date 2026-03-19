package com.bbss.shared.events;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Kafka domain event published whenever the state of a financial transaction
 * changes within the BBSS platform.
 *
 * <p>Producers: {@code transaction-service}, {@code blockchain-service}
 * <br>Consumers: {@code audit-service}, {@code fraud-detection-service},
 *                {@code notification-service}, {@code blockchain-service}
 *
 * <p>Topic naming convention: {@code bbss.transactions.events}
 *
 * <p>Serialisation: Jackson (JSON) via Spring Kafka's
 * {@code JsonSerializer} / {@code JsonDeserializer} pair.  The class also
 * implements {@link Serializable} for optional JVM-level serialisation in
 * test environments or in-memory brokers.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionEvent implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // ── Identity ──────────────────────────────────────────────────────────────

    /**
     * Globally unique event identifier (UUID v4).
     * Used for idempotent processing: consumers must deduplicate on this key.
     */
    private String eventId;

    /**
     * Identifier of the transaction that this event describes.
     * Correlates with the {@code transactions} table primary key.
     */
    private String transactionId;

    /**
     * Tenant that owns this transaction.
     * Consumers must only process events that belong to their authorised tenants.
     */
    private String tenantId;

    // ── Transaction data ──────────────────────────────────────────────────────

    /** Debited account number (masked or full depending on consumer trust level). */
    private String fromAccount;

    /** Credited account number. */
    private String toAccount;

    /**
     * Monetary amount.  Uses {@link BigDecimal} to avoid floating-point
     * precision issues with financial values.
     */
    private BigDecimal amount;

    /**
     * ISO 4217 currency code (e.g. "USD", "EUR", "GBP").
     */
    private String currency;

    /**
     * Current lifecycle status of the transaction.
     * Consumers should treat transitions as a state machine:
     * SUBMITTED → VERIFIED → COMPLETED
     *          ↘ FRAUD_HOLD → BLOCKED
     *                       ↘ COMPLETED (after manual review)
     */
    private String status;

    // ── Metadata ──────────────────────────────────────────────────────────────

    /** Server-side instant at which this event was created. */
    private LocalDateTime timestamp;

    /**
     * Optional correlation identifier propagated from the originating HTTP
     * request (value of {@code X-Correlation-Id} header).  Useful for
     * distributed tracing across service boundaries.
     */
    private String correlationId;

    // ── Status enum ───────────────────────────────────────────────────────────

    /**
     * Lifecycle states of a BBSS transaction.
     *
     * <ul>
     *   <li>{@link #SUBMITTED}   – transaction has been received and persisted</li>
     *   <li>{@link #VERIFIED}    – passed basic validation and compliance checks</li>
     *   <li>{@link #FRAUD_HOLD}  – flagged by the fraud detection engine; pending review</li>
     *   <li>{@link #BLOCKED}     – permanently rejected (fraud confirmed or compliance breach)</li>
     *   <li>{@link #COMPLETED}   – successfully settled on the blockchain ledger</li>
     *   <li>{@link #FAILED}      – processing failed due to a technical error</li>
     * </ul>
     */
    public enum TransactionStatus {
        SUBMITTED,
        VERIFIED,
        FRAUD_HOLD,
        BLOCKED,
        COMPLETED,
        FAILED
    }
}
