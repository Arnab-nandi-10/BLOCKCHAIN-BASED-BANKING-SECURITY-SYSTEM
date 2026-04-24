package com.bbss.blockchain.messaging;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Publishes blockchain-level events to Apache Kafka after a block is committed
 * on the Hyperledger Fabric channel.
 *
 * <p>Topics used:
 * <ul>
 *   <li>{@code block.committed} — emitted for every successful ledger commit</li>
 *   <li>{@code block.commit.failed} — emitted when the ledger write remains pending and will be retried</li>
 *   <li>{@code block.verification.updated} — emitted when a manual integrity check recalculates the verification state</li>
 * </ul>
 * </p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BlockchainEventPublisher {

    private static final String TOPIC_BLOCK_COMMITTED = "block.committed";
    private static final String TOPIC_BLOCK_COMMIT_FAILED = "block.commit.failed";
    private static final String TOPIC_BLOCK_VERIFICATION_UPDATED = "block.verification.updated";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    /**
     * Publish a block-committed notification to the {@code block.committed} topic.
     *
     * <p>Downstream consumers (e.g. audit service, notification service) can react
     * to this event to confirm settlement and update their own state.</p>
     *
     * @param transactionId business transaction identifier
     * @param blockNumber   the block number in which the transaction was committed
     * @param txId          the Hyperledger Fabric transaction hash
     * @param tenantId      the tenant owning the transaction
     * @param verificationStatus integrity verification outcome
     */
    public void publishBlockCommitted(
            String transactionId,
            String blockNumber,
            String txId,
            String tenantId,
            String verificationStatus,
            String payloadHash,
            String recordHash,
            String previousHash) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("eventId",     UUID.randomUUID().toString());
        event.put("transactionId", transactionId);
        event.put("blockNumber", blockNumber);
        event.put("txId",        txId);
        event.put("tenantId",    tenantId);
        event.put("verificationStatus", verificationStatus);
        event.put("payloadHash", payloadHash);
        event.put("recordHash", recordHash);
        event.put("previousHash", previousHash);
        event.put("timestamp",   LocalDateTime.now().toString());

        String messageKey = txId != null ? txId : UUID.randomUUID().toString();

        CompletableFuture<SendResult<String, Object>> future =
                kafkaTemplate.send(TOPIC_BLOCK_COMMITTED, messageKey, event);

        future.whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("Failed to publish BlockCommittedEvent for txId={} blockNumber={}: {}",
                        txId, blockNumber, ex.getMessage(), ex);
            } else {
                log.debug("BlockCommittedEvent published: transactionId={} txId={} blockNumber={} partition={} offset={}",
                        transactionId, txId, blockNumber,
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
            }
        });
    }

    public void publishBlockCommitFailed(String transactionId, String tenantId, String message) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("eventId", UUID.randomUUID().toString());
        event.put("transactionId", transactionId);
        event.put("tenantId", tenantId);
        event.put("message", message);
        event.put("timestamp", LocalDateTime.now().toString());

        CompletableFuture<SendResult<String, Object>> future =
                kafkaTemplate.send(TOPIC_BLOCK_COMMIT_FAILED, transactionId, event);

        future.whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("Failed to publish BlockCommitFailedEvent for transactionId={}: {}",
                        transactionId, ex.getMessage(), ex);
            } else {
                log.debug("BlockCommitFailedEvent published: transactionId={} partition={} offset={}",
                        transactionId,
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
            }
        });
    }

    public void publishVerificationUpdated(
            String entityType,
            String recordId,
            String tenantId,
            String verificationStatus,
            boolean valid,
            String payloadHash,
            String recordHash,
            String previousHash,
            String verifiedAt) {

        Map<String, Object> event = new LinkedHashMap<>();
        event.put("eventId", UUID.randomUUID().toString());
        event.put("entityType", entityType);
        event.put("recordId", recordId);
        event.put("tenantId", tenantId);
        event.put("verificationStatus", verificationStatus);
        event.put("valid", valid);
        event.put("payloadHash", payloadHash);
        event.put("recordHash", recordHash);
        event.put("previousHash", previousHash);
        event.put("verifiedAt", verifiedAt != null ? verifiedAt : LocalDateTime.now().toString());
        event.put("timestamp", LocalDateTime.now().toString());

        if ("TRANSACTION".equalsIgnoreCase(entityType)) {
            event.put("transactionId", recordId);
        } else if ("AUDIT".equalsIgnoreCase(entityType)) {
            event.put("auditId", recordId);
        }

        CompletableFuture<SendResult<String, Object>> future =
                kafkaTemplate.send(
                        TOPIC_BLOCK_VERIFICATION_UPDATED,
                        recordId != null ? recordId : UUID.randomUUID().toString(),
                        event
                );

        future.whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("Failed to publish BlockVerificationUpdatedEvent for {} {}: {}",
                        entityType, recordId, ex.getMessage(), ex);
            } else {
                log.debug("BlockVerificationUpdatedEvent published: entityType={} recordId={} status={} partition={} offset={}",
                        entityType,
                        recordId,
                        verificationStatus,
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
            }
        });
    }
}
