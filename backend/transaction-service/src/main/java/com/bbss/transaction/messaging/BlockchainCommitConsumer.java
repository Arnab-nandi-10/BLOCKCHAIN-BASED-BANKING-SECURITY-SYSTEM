package com.bbss.transaction.messaging;

import com.bbss.transaction.service.TransactionService;
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
 * Updates transaction-service state when blockchain-service finishes or defers a Fabric write.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BlockchainCommitConsumer {

    private static final String TOPIC_BLOCK_COMMITTED = "block.committed";
    private static final String TOPIC_BLOCK_COMMIT_FAILED = "block.commit.failed";
    private static final String TOPIC_BLOCK_VERIFICATION_UPDATED = "block.verification.updated";

    private final TransactionService transactionService;

    @KafkaListener(
            topics = {
                    TOPIC_BLOCK_COMMITTED,
                    TOPIC_BLOCK_COMMIT_FAILED,
                    TOPIC_BLOCK_VERIFICATION_UPDATED
            },
            groupId = "transaction-service",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(
            @Payload Map<String, Object> event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        String transactionId = stringValue(event.get("transactionId"));
        log.info("Received {} event for transactionId={} partition={} offset={}",
                topic, transactionId, partition, offset);

        try {
            switch (topic) {
                case TOPIC_BLOCK_COMMITTED -> transactionService.markLedgerCommitted(
                        transactionId,
                        stringValue(event.get("txId")),
                        stringValue(event.get("blockNumber")),
                        stringValue(event.get("verificationStatus")),
                        stringValue(event.get("payloadHash")),
                        stringValue(event.get("recordHash")),
                        stringValue(event.get("previousHash"))
                );
                case TOPIC_BLOCK_COMMIT_FAILED -> transactionService.markLedgerUnavailable(
                        transactionId,
                        stringValue(event.get("message"))
                );
                case TOPIC_BLOCK_VERIFICATION_UPDATED -> {
                    if (!"TRANSACTION".equalsIgnoreCase(stringValue(event.get("entityType")))) {
                        return; // ACK'd in finally
                    }
                    transactionService.updateVerificationStatus(
                            transactionId,
                            stringValue(event.get("verificationStatus")),
                            stringValue(event.get("payloadHash")),
                            stringValue(event.get("recordHash")),
                            stringValue(event.get("previousHash"))
                    );
                }
                default -> log.warn("Unrecognised topic {} — message will be ACK'd", topic);
            }
        } catch (Exception ex) {
            log.error("Failed to process {} event for transactionId={}: {} — message will be ACK'd to prevent infinite redelivery",
                    topic, transactionId, ex.getMessage(), ex);
        } finally {
            acknowledgment.acknowledge();
        }
    }

    private String stringValue(Object value) {
        return value != null ? value.toString() : null;
    }
}
