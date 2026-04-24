package com.bbss.blockchain.messaging;

import com.bbss.shared.events.AuditEvent;
import com.bbss.blockchain.service.ChaincodeInvokerService;
import com.bbss.blockchain.service.FabricGatewayService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/**
 * Kafka consumer that persists {@link AuditEvent} messages to the Hyperledger
 * Fabric audit chaincode.
 *
 * <p>Listens on the {@code audit.entry} topic within the {@code blockchain-service}
 * consumer group. Each event is forwarded to
 * {@link ChaincodeInvokerService#submitAuditEntry(AuditEvent)} for on-chain persistence.</p>
 */

@ConditionalOnProperty(name = "fabric.enabled", havingValue = "true", matchIfMissing = false)
@Component
@RequiredArgsConstructor
@Slf4j
public class AuditEventConsumer {

    private final ChaincodeInvokerService chaincodeInvokerService;
    private final ObjectMapper objectMapper;

    /**
     * Consume an {@link AuditEvent} and write it to the Fabric audit chaincode.
     *
     * @param eventPayload   the deserialized audit event payload
     * @param partition      Kafka partition (for observability)
     * @param offset         Kafka offset (for observability)
     * @param acknowledgment manual acknowledgment handle
     */
    @KafkaListener(
            topics = "audit.entry",
            groupId = "blockchain-service",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(
            @Payload Object eventPayload,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        AuditEvent event = objectMapper.convertValue(eventPayload, AuditEvent.class);

        log.info("Received AuditEvent: eventId={} entityType={} action={} partition={} offset={}",
                event.getEventId(), event.getEntityType(), event.getAction(), partition, offset);

        try {
            chaincodeInvokerService.submitAuditEntry(event);
            acknowledgment.acknowledge();
            log.debug("AuditEvent {} successfully committed to Fabric ledger", event.getEventId());
        } catch (FabricGatewayService.BlockchainServiceException ex) {
            log.error("Failed to commit AuditEvent {} to Fabric ledger: {}",
                    event.getEventId(), ex.getMessage(), ex);
            // Do not acknowledge — the consumer will retry on the next poll.
        } catch (Exception ex) {
            log.error("Unexpected error processing AuditEvent {}: {}",
                    event.getEventId(), ex.getMessage(), ex);
            // Do not acknowledge — allow the retry / dead-letter policy to handle it.
        }
    }
}
