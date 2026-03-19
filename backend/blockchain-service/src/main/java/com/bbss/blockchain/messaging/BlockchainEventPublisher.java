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
 * </ul>
 * </p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BlockchainEventPublisher {

    private static final String TOPIC_BLOCK_COMMITTED = "block.committed";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    /**
     * Publish a block-committed notification to the {@code block.committed} topic.
     *
     * <p>Downstream consumers (e.g. audit service, notification service) can react
     * to this event to confirm settlement and update their own state.</p>
     *
     * @param blockNumber    the block number in which the transaction was committed
     * @param txId           the Hyperledger Fabric transaction hash
     * @param tenantId       the tenant owning the transaction
     */
    public void publishBlockCommitted(String blockNumber, String txId, String tenantId) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("eventId",     UUID.randomUUID().toString());
        event.put("blockNumber", blockNumber);
        event.put("txId",        txId);
        event.put("tenantId",    tenantId);
        event.put("timestamp",   LocalDateTime.now().toString());

        String messageKey = txId != null ? txId : UUID.randomUUID().toString();

        CompletableFuture<SendResult<String, Object>> future =
                kafkaTemplate.send(TOPIC_BLOCK_COMMITTED, messageKey, event);

        future.whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("Failed to publish BlockCommittedEvent for txId={} blockNumber={}: {}",
                        txId, blockNumber, ex.getMessage(), ex);
            } else {
                log.debug("BlockCommittedEvent published: txId={} blockNumber={} partition={} offset={}",
                        txId, blockNumber,
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
            }
        });
    }
}
