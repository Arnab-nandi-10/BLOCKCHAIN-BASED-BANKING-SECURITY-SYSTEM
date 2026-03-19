package com.bbss.transaction.messaging;

import com.bbss.shared.events.FraudAlertEvent;
import com.bbss.transaction.domain.model.Transaction;
import com.bbss.transaction.domain.model.TransactionStatus;
import com.bbss.transaction.domain.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consumes {@link FraudAlertEvent} messages from the {@code fraud.alert} Kafka topic.
 *
 * <p>When the fraud-detection service (or another downstream processor) enriches a
 * fraud alert and re-publishes it, this consumer updates the corresponding
 * {@link Transaction} with the latest fraud score, risk level, and status.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class FraudResultConsumer {

    private final TransactionRepository transactionRepository;

    /**
     * Consume a {@link FraudAlertEvent} and update the associated transaction.
     *
     * <p>If the event's {@code shouldBlock} flag is {@code true} the transaction is
     * transitioned to {@link TransactionStatus#BLOCKED}; otherwise it is left on
     * {@link TransactionStatus#FRAUD_HOLD} pending manual review.</p>
     *
     * @param event           the deserialized fraud alert event
     * @param partition       Kafka partition (for logging)
     * @param offset          Kafka offset (for logging)
     * @param acknowledgment  manual acknowledgment handle
     */
    @KafkaListener(
            topics = "fraud.alert",
            groupId = "transaction-service",
            containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional
    public void consume(
            @Payload FraudAlertEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        log.info("Received FraudAlertEvent: txId={} score={} riskLevel={} partition={} offset={}",
                event.getTransactionId(), event.getFraudScore(), event.getRiskLevel(), partition, offset);

        try {
            transactionRepository.findByTransactionId(event.getTransactionId())
                    .ifPresentOrElse(
                            tx -> updateTransaction(tx, event),
                            () -> log.warn("FraudAlertEvent received for unknown txId={}",
                                    event.getTransactionId())
                    );
            acknowledgment.acknowledge();
        } catch (Exception ex) {
            log.error("Error processing FraudAlertEvent for txId={}: {}",
                    event.getTransactionId(), ex.getMessage(), ex);
            // Do not acknowledge — let the consumer retry.
        }
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private void updateTransaction(Transaction tx, FraudAlertEvent event) {
        tx.setFraudScore(event.getFraudScore());
        tx.setFraudRiskLevel(event.getRiskLevel());

        if ("BLOCK".equals(event.getRecommendation())) {
            tx.setStatus(TransactionStatus.BLOCKED);
            tx.setRejectionReason(
                    "Fraud engine escalated to BLOCKED: " + event.getRecommendation());
            log.warn("Transaction {} escalated to BLOCKED based on FraudAlertEvent", tx.getTransactionId());
        } else {
            tx.setStatus(TransactionStatus.FRAUD_HOLD);
            log.info("Transaction {} updated to FRAUD_HOLD based on FraudAlertEvent", tx.getTransactionId());
        }

        transactionRepository.save(tx);
        log.debug("Transaction {} updated with fraud result: score={} riskLevel={}",
                tx.getTransactionId(), event.getFraudScore(), event.getRiskLevel());
    }
}
