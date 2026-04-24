package com.bbss.transaction.messaging;

import com.bbss.shared.events.FraudAlertEvent;
import com.bbss.shared.events.TransactionEvent;
import com.bbss.transaction.client.FraudServiceClient.FraudScoreResponse;
import com.bbss.transaction.domain.model.Transaction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Publishes domain events to Apache Kafka topics for downstream service consumption.
 *
 * <p>Topics used:
 * <ul>
 *   <li>{@code tx.submitted}   — emitted when a transaction is first persisted</li>
 *   <li>{@code tx.ledger.commit.request} — emitted after the fraud decision so blockchain-service can anchor it asynchronously</li>
 *   <li>{@code tx.verified}    — emitted after successful blockchain submission</li>
 *   <li>{@code tx.blocked}     — emitted when fraud engine blocks a transaction</li>
 *   <li>{@code fraud.alert}    — emitted when a transaction is placed on FRAUD_HOLD</li>
 * </ul>
 * </p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TransactionEventPublisher {

    private static final String TOPIC_TX_SUBMITTED = "tx.submitted";
    private static final String TOPIC_TX_LEDGER_COMMIT_REQUEST = "tx.ledger.commit.request";
    private static final String TOPIC_TX_VERIFIED  = "tx.verified";
    private static final String TOPIC_TX_BLOCKED   = "tx.blocked";
    private static final String TOPIC_FRAUD_ALERT  = "fraud.alert";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    // -------------------------------------------------------------------------
    // Public publisher methods
    // -------------------------------------------------------------------------

    /**
     * Publish a {@code TransactionEvent} to the {@code tx.submitted} topic.
     *
     * @param tx the newly created / retried transaction
     */
    public void publishTransactionSubmitted(Transaction tx) {
        TransactionEvent event = buildTransactionEvent(tx);
        sendAsync(TOPIC_TX_SUBMITTED, tx.getTransactionId(), event);
        log.debug("Published TransactionEvent[SUBMITTED] for txId={}", tx.getTransactionId());
    }

    /**
     * Publish a {@code TransactionEvent} to the {@code tx.verified} topic.
     *
     * @param tx the transaction that has been verified on the blockchain
     */
    public void publishTransactionVerified(Transaction tx) {
        TransactionEvent event = buildTransactionEvent(tx);
        sendAsync(TOPIC_TX_VERIFIED, tx.getTransactionId(), event);
        log.debug("Published TransactionEvent[VERIFIED] for txId={}", tx.getTransactionId());
    }

    /**
     * Publish a request for blockchain-service to anchor the post-fraud decision.
     *
     * @param tx the transaction whose fraud outcome must be written to Fabric
     */
    public void publishLedgerCommitRequested(Transaction tx) {
        TransactionEvent event = buildTransactionEvent(tx);
        sendAsync(TOPIC_TX_LEDGER_COMMIT_REQUEST, tx.getTransactionId(), event);
        log.debug("Published TransactionEvent[LEDGER_COMMIT_REQUEST] for txId={}", tx.getTransactionId());
    }

    /**
     * Publish a {@code TransactionEvent} to the {@code tx.blocked} topic.
     *
     * @param tx the transaction that was blocked by the fraud engine
     */
    public void publishTransactionBlocked(Transaction tx) {
        TransactionEvent event = buildTransactionEvent(tx);
        sendAsync(TOPIC_TX_BLOCKED, tx.getTransactionId(), event);
        log.debug("Published TransactionEvent[BLOCKED] for txId={}", tx.getTransactionId());
    }

    /**
     * Publish a {@code FraudAlertEvent} to the {@code fraud.alert} topic.
     *
     * @param tx          the transaction placed on FRAUD_HOLD
     * @param fraudResult the fraud scoring result from the fraud engine
     */
    public void publishFraudAlert(Transaction tx, FraudScoreResponse fraudResult) {
        FraudAlertEvent event = FraudAlertEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .transactionId(tx.getTransactionId())
                .tenantId(tx.getTenantId())
                .fraudScore(fraudResult.score())
                .riskLevel(fraudResult.riskLevel())
                .triggeredRules(fraudResult.triggeredRules())
                .recommendation(fraudResult.recommendation())
                .detectedAt(LocalDateTime.now())
                .build();

        sendAsync(TOPIC_FRAUD_ALERT, tx.getTransactionId(), event);
        log.debug("Published FraudAlertEvent for txId={} score={}",
                tx.getTransactionId(), fraudResult.score());
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private TransactionEvent buildTransactionEvent(Transaction tx) {
        return TransactionEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .transactionId(tx.getTransactionId())
                .tenantId(tx.getTenantId())
                .fromAccount(tx.getFromAccount())
                .toAccount(tx.getToAccount())
                .amount(tx.getAmount())
                .currency(tx.getCurrency())
                .transactionType(tx.getType() != null ? tx.getType().name() : null)
                .status(tx.getStatus() != null ? tx.getStatus().name() : null)
                .ledgerStatus(tx.getLedgerStatus() != null ? tx.getLedgerStatus().name() : null)
                .verificationStatus(tx.getVerificationStatus() != null ? tx.getVerificationStatus().name() : null)
                .fraudScore(tx.getFraudScore())
                .riskLevel(tx.getFraudRiskLevel())
                .decision(tx.getFraudDecision())
                .recommendation(tx.getFraudRecommendation())
                .reviewRequired(tx.isReviewRequired())
                .triggeredRules(tx.getTriggeredRules())
                .explanations(tx.getExplanations())
                .blockchainTxId(tx.getBlockchainTxId())
                .blockNumber(tx.getBlockNumber())
                .timestamp(LocalDateTime.now())
                .transactionTimestamp(tx.getCreatedAt())
                .ipAddress(tx.getIpAddress())
                .metadata(tx.getMetadata())
                .correlationId(tx.getCorrelationId())
                .build();
    }

    private void sendAsync(String topic, String key, Object payload) {
        CompletableFuture<SendResult<String, Object>> future =
                kafkaTemplate.send(topic, key, payload);

        future.whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("Failed to publish event to topic={} key={}: {}",
                        topic, key, ex.getMessage(), ex);
            } else {
                log.trace("Event delivered to topic={} partition={} offset={}",
                        topic,
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
            }
        });
    }
}
