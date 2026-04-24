package com.bbss.audit.messaging;

import com.bbss.audit.service.AuditService;
import com.bbss.shared.events.AuditEvent;
import com.bbss.shared.events.FraudAlertEvent;
import com.bbss.shared.events.TenantEvent;
import com.bbss.shared.events.TransactionEvent;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Kafka consumer that ingests domain events from upstream services and converts
 * them into {@link AuditEvent} objects for the audit trail.
 *
 * <p>Subscribed topics:
 * <ul>
 *   <li>{@code tx.submitted}       – transaction was created and accepted by transaction-service</li>
 *   <li>{@code tx.verified}        – transaction passed validation</li>
 *   <li>{@code tx.blocked}         – transaction was permanently rejected</li>
 *   <li>{@code fraud.alert}        – fraud engine detected a high-risk transaction</li>
 *   <li>{@code tenant.provisioned} – a new tenant was successfully onboarded</li>
 *   <li>{@code block.committed}    – transaction was successfully anchored on Fabric</li>
 *   <li>{@code block.commit.failed} – ledger anchoring is pending and will be retried</li>
 *   <li>{@code auth.user.registered} – a new tenant user was created</li>
 * </ul>
 *
 * <p>Deserialization strategy: the Kafka consumer is configured with
 * {@code VALUE_DEFAULT_TYPE = java.util.Map} so each record value arrives as a
 * {@code Map<String, Object>}.  {@link ObjectMapper#convertValue} is then used
 * to coerce the map into the appropriate typed event class based on the topic.
 *
 * <p>Any exception thrown during processing is caught and logged; the Kafka
 * offset is committed regardless to prevent poison-pill messages from blocking
 * the partition indefinitely.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AuditEventConsumer {

    private static final String TOPIC_TX_SUBMITTED       = "tx.submitted";
    private static final String TOPIC_TX_VERIFIED        = "tx.verified";
    private static final String TOPIC_TX_BLOCKED         = "tx.blocked";
    private static final String TOPIC_FRAUD_ALERT        = "fraud.alert";
    private static final String TOPIC_TENANT_PROVISIONED = "tenant.provisioned";
    private static final String TOPIC_BLOCK_COMMITTED    = "block.committed";
    private static final String TOPIC_BLOCK_COMMIT_FAILED = "block.commit.failed";
    private static final String TOPIC_AUTH_USER_REGISTERED = "auth.user.registered";

    private final AuditService  auditService;
    private final ObjectMapper  objectMapper;

    /**
     * Processes a Kafka record from any of the subscribed topics.
     *
     * @param record the raw consumer record; key is the partition routing key
     *               (usually tenantId or transactionId), value is a
     *               {@code Map<String, Object>} after JSON deserialization
     */
    @KafkaListener(
        topics  = {
            TOPIC_TX_SUBMITTED,
            TOPIC_TX_VERIFIED,
            TOPIC_TX_BLOCKED,
            TOPIC_FRAUD_ALERT,
            TOPIC_TENANT_PROVISIONED,
            TOPIC_BLOCK_COMMITTED,
            TOPIC_BLOCK_COMMIT_FAILED,
            TOPIC_AUTH_USER_REGISTERED
        },
        groupId = "audit-service"
    )
    public void consume(ConsumerRecord<String, Object> record) {
        String topic = record.topic();
        log.debug("Received Kafka record: topic={} partition={} offset={}",
                topic, record.partition(), record.offset());

        try {
            Map<String, Object> data = toMap(record.value());
            AuditEvent auditEvent   = buildAuditEvent(topic, data);

            if (auditEvent != null) {
                auditService.createAuditEntry(auditEvent);
            }

        } catch (Exception e) {
            log.error("Failed to process Kafka record: topic={} offset={} error={}",
                    topic, record.offset(), e.getMessage(), e);
        }
    }

    // ── Private mapping helpers ───────────────────────────────────────────────

    /**
     * Dispatches to the correct mapping method based on the topic name.
     *
     * @param topic the Kafka topic the record was consumed from
     * @param data  the raw event data as a map
     * @return a populated {@link AuditEvent}, or {@code null} if the topic is
     *         unrecognised
     */
    private AuditEvent buildAuditEvent(String topic, Map<String, Object> data) {
        return switch (topic) {
            case TOPIC_TX_SUBMITTED       -> fromTransactionEvent(data, "TRANSACTION_SUBMITTED");
            case TOPIC_TX_VERIFIED        -> fromTransactionEvent(data, "TRANSACTION_VERIFIED");
            case TOPIC_TX_BLOCKED         -> fromTransactionEvent(data, "TRANSACTION_BLOCKED");
            case TOPIC_FRAUD_ALERT        -> fromFraudAlertEvent(data);
            case TOPIC_TENANT_PROVISIONED -> fromTenantEvent(data);
            case TOPIC_BLOCK_COMMITTED    -> fromBlockchainCommitEvent(data);
            case TOPIC_BLOCK_COMMIT_FAILED -> fromBlockchainCommitFailedEvent(data);
            case TOPIC_AUTH_USER_REGISTERED -> fromUserRegisteredEvent(data);
            default -> {
                log.warn("Unrecognised Kafka topic: {}", topic);
                yield null;
            }
        };
    }

    /**
     * Maps a {@code tx.verified} or {@code tx.blocked} payload to an
     * {@link AuditEvent}.
     */
    private AuditEvent fromTransactionEvent(Map<String, Object> data, String action) {
        TransactionEvent txEvent = objectMapper.convertValue(data, TransactionEvent.class);

        String auditId       = txEvent.getEventId() != null
                ? txEvent.getEventId()
                : UUID.randomUUID().toString();
        String tenantId      = txEvent.getTenantId()      != null ? txEvent.getTenantId()      : "UNKNOWN";
        String transactionId = txEvent.getTransactionId() != null ? txEvent.getTransactionId() : "UNKNOWN";
        LocalDateTime occurredAt = txEvent.getTimestamp() != null
                ? txEvent.getTimestamp()
                : LocalDateTime.now();

        return AuditEvent.builder()
                .eventId(auditId)
                .tenantId(tenantId)
                .entityType("TRANSACTION")
                .entityId(transactionId)
                .action(action)
                .actorId("TRANSACTION_SERVICE")
                .actorType("SYSTEM")
                .ipAddress(null)
                .payload(txEvent)
                .occurredAt(occurredAt)
                .correlationId(txEvent.getCorrelationId())
                .build();
    }

    /**
     * Maps a {@code fraud.alert} payload to an {@link AuditEvent}.
     */
    private AuditEvent fromFraudAlertEvent(Map<String, Object> data) {
        FraudAlertEvent fraudEvent = objectMapper.convertValue(data, FraudAlertEvent.class);

        String auditId       = fraudEvent.getEventId()       != null ? fraudEvent.getEventId()       : UUID.randomUUID().toString();
        String tenantId      = fraudEvent.getTenantId()      != null ? fraudEvent.getTenantId()      : "UNKNOWN";
        String transactionId = fraudEvent.getTransactionId() != null ? fraudEvent.getTransactionId() : "UNKNOWN";
        LocalDateTime occurredAt = fraudEvent.getDetectedAt() != null
                ? fraudEvent.getDetectedAt()
                : LocalDateTime.now();

        return AuditEvent.builder()
                .eventId(auditId)
                .tenantId(tenantId)
                .entityType("TRANSACTION")
                .entityId(transactionId)
                .action("FRAUD_DETECTED")
                .actorId("FRAUD_DETECTION_SERVICE")
                .actorType("SYSTEM")
                .ipAddress(null)
                .payload(fraudEvent)
                .occurredAt(occurredAt)
                .correlationId(null)
                .build();
    }

    /**
     * Maps a {@code tenant.provisioned} payload to an {@link AuditEvent}.
     */
    private AuditEvent fromTenantEvent(Map<String, Object> data) {
        TenantEvent tenantEvent = objectMapper.convertValue(data, TenantEvent.class);

        String auditId  = tenantEvent.getEventId()  != null ? tenantEvent.getEventId()  : UUID.randomUUID().toString();
        String tenantId = tenantEvent.getTenantId() != null ? tenantEvent.getTenantId() : "UNKNOWN";
        LocalDateTime occurredAt = tenantEvent.getTimestamp() != null
                ? tenantEvent.getTimestamp()
                : LocalDateTime.now();

        String actorId = tenantEvent.getAdminEmail() != null
                ? tenantEvent.getAdminEmail()
                : "TENANT_SERVICE";

        return AuditEvent.builder()
                .eventId(auditId)
                .tenantId(tenantId)
                .entityType("TENANT")
                .entityId(tenantId)
                .action("TENANT_PROVISIONED")
                .actorId(actorId)
                .actorType("SYSTEM")
                .ipAddress(null)
                .payload(tenantEvent)
                .occurredAt(occurredAt)
                .correlationId(null)
                .build();
    }

    /**
     * Maps a {@code block.committed} payload to an {@link AuditEvent}.
     */
    private AuditEvent fromBlockchainCommitEvent(Map<String, Object> data) {
        String eventId = stringValue(data.get("eventId"), UUID.randomUUID().toString());
        String tenantId = stringValue(data.get("tenantId"), "UNKNOWN");
        String transactionId = stringValue(data.get("transactionId"), "UNKNOWN");

        return AuditEvent.builder()
                .eventId(eventId)
                .tenantId(tenantId)
                .entityType("TRANSACTION")
                .entityId(transactionId)
                .action("BLOCKCHAIN_COMMITTED")
                .actorId("BLOCKCHAIN_SERVICE")
                .actorType("SYSTEM")
                .ipAddress(null)
                .payload(data)
                .occurredAt(dateTimeValue(data.get("timestamp")))
                .correlationId(null)
                .build();
    }

    /**
     * Maps a {@code block.commit.failed} payload to an {@link AuditEvent}.
     */
    private AuditEvent fromBlockchainCommitFailedEvent(Map<String, Object> data) {
        String eventId = stringValue(data.get("eventId"), UUID.randomUUID().toString());
        String tenantId = stringValue(data.get("tenantId"), "UNKNOWN");
        String transactionId = stringValue(data.get("transactionId"), "UNKNOWN");

        return AuditEvent.builder()
                .eventId(eventId)
                .tenantId(tenantId)
                .entityType("TRANSACTION")
                .entityId(transactionId)
                .action("BLOCKCHAIN_PENDING")
                .actorId("BLOCKCHAIN_SERVICE")
                .actorType("SYSTEM")
                .ipAddress(null)
                .payload(data)
                .occurredAt(dateTimeValue(data.get("timestamp")))
                .correlationId(null)
                .build();
    }

    /**
     * Maps an {@code auth.user.registered} payload to an {@link AuditEvent}.
     */
    private AuditEvent fromUserRegisteredEvent(Map<String, Object> data) {
        String eventId = stringValue(data.get("eventId"), UUID.randomUUID().toString());
        String tenantId = stringValue(data.get("tenantId"), "UNKNOWN");
        String userId = stringValue(data.get("userId"), "UNKNOWN");
        String actorId = stringValue(data.get("email"), "AUTH_SERVICE");

        return AuditEvent.builder()
                .eventId(eventId)
                .tenantId(tenantId)
                .entityType("USER")
                .entityId(userId)
                .action("USER_REGISTERED")
                .actorId(actorId)
                .actorType("SYSTEM")
                .ipAddress(null)
                .payload(data)
                .occurredAt(dateTimeValue(data.get("registeredAt")))
                .correlationId(null)
                .build();
    }

    /**
     * Safely converts the Kafka record value (already deserialized as a
     * {@code Map<String, Object>} by the Spring Kafka JSON deserializer) to
     * a typed map.  If for any reason the value is not a Map (e.g. a plain
     * String or a previously typed object), it falls back to an empty map
     * to avoid NPEs.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> toMap(Object value) {
        if (value instanceof Map<?, ?> m) {
            return (Map<String, Object>) m;
        }
        if (value instanceof String json) {
            try {
                return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
            } catch (Exception e) {
                log.warn("Could not parse Kafka record value as JSON map: {}", e.getMessage());
                return Map.of();
            }
        }
        return objectMapper.convertValue(value, new TypeReference<Map<String, Object>>() {});
    }

    private String stringValue(Object value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String text = value.toString();
        return text.isBlank() ? fallback : text;
    }

    private LocalDateTime dateTimeValue(Object value) {
        if (value == null) {
            return LocalDateTime.now();
        }
        if (value instanceof LocalDateTime dateTime) {
            return dateTime;
        }
        try {
            return LocalDateTime.parse(value.toString());
        } catch (Exception ex) {
            log.debug("Could not parse audit event timestamp '{}': {}", value, ex.getMessage());
            return LocalDateTime.now();
        }
    }
}
