package com.bbss.audit.messaging;

import com.bbss.audit.service.AuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Persists manual blockchain verification refreshes back into audit-service state.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BlockchainVerificationConsumer {

    private static final String TOPIC_BLOCK_VERIFICATION_UPDATED = "block.verification.updated";

    private final AuditService auditService;

    @KafkaListener(
            topics = TOPIC_BLOCK_VERIFICATION_UPDATED,
            groupId = "audit-service",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(
            @Payload Map<String, Object> event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        String entityType = stringValue(event.get("entityType"));
        String auditId = stringValue(event.get("auditId"));
        log.debug("Received verification refresh event: entityType={} auditId={} partition={} offset={}",
                entityType, auditId, partition, offset);

        try {
            if ("AUDIT".equalsIgnoreCase(entityType) && auditId != null) {
                auditService.updateVerificationStatus(
                        auditId,
                        stringValue(event.get("verificationStatus"))
                );
            }

            acknowledgment.acknowledge();
        } catch (Exception ex) {
            log.error("Failed to process verification refresh for auditId={}: {}",
                    auditId, ex.getMessage(), ex);
        }
    }

    private String stringValue(Object value) {
        return value != null ? value.toString() : null;
    }
}
