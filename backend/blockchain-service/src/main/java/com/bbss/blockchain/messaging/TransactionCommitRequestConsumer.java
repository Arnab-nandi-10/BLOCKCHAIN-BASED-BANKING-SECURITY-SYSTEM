package com.bbss.blockchain.messaging;

import com.bbss.blockchain.dto.BlockchainSubmitRequest;
import com.bbss.blockchain.service.ChaincodeInvokerService;
import com.bbss.shared.events.TransactionEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/**
 * Consumes asynchronous ledger commit requests emitted by transaction-service after the fraud decision.
 */
@ConditionalOnProperty(name = "fabric.enabled", havingValue = "true", matchIfMissing = false)
@Component
@RequiredArgsConstructor
@Slf4j
public class TransactionCommitRequestConsumer {

    private final ChaincodeInvokerService chaincodeInvokerService;
    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics = "tx.ledger.commit.request",
            groupId = "blockchain-service",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(
            @Payload Object eventPayload,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        TransactionEvent event = objectMapper.convertValue(eventPayload, TransactionEvent.class);
        log.info("Received ledger commit request: txId={} status={} partition={} offset={}",
                event.getTransactionId(), event.getStatus(), partition, offset);

        try {
            BlockchainSubmitRequest request = new BlockchainSubmitRequest(
                    event.getTransactionId(),
                    event.getTenantId(),
                    event.getFromAccount(),
                    event.getToAccount(),
                    event.getAmount(),
                    event.getCurrency(),
                    event.getTransactionType(),
                    event.getStatus(),
                    event.getFraudScore(),
                    event.getRiskLevel(),
                    event.getDecision(),
                    event.getRecommendation(),
                    event.getTransactionTimestamp(),
                    event.getIpAddress(),
                    event.getMetadata()
            );

            chaincodeInvokerService.submitTransactionToLedger(request);
            acknowledgment.acknowledge();
        } catch (Exception ex) {
            log.error("Failed to process ledger commit request for txId={}: {}",
                    event.getTransactionId(), ex.getMessage(), ex);
        }
    }
}
