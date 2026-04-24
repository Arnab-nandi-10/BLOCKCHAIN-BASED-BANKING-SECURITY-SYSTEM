package com.bbss.transaction.messaging;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.springframework.kafka.support.Acknowledgment;

import com.bbss.shared.events.FraudAlertEvent;
import com.bbss.transaction.domain.model.Transaction;
import com.bbss.transaction.domain.model.TransactionStatus;
import com.bbss.transaction.domain.repository.TransactionRepository;

class FraudResultConsumerTest {

    @Test
    void blocksTransactionWhenRecommendationIsBlockTransaction() {
        TransactionRepository repository = mock(TransactionRepository.class);
        Transaction tx = baseTransaction();
        when(repository.findByTransactionId("tx-123")).thenReturn(Optional.of(tx));
        when(repository.save(any(Transaction.class))).thenAnswer(invocation -> invocation.getArgument(0));

        FraudResultConsumer consumer = new FraudResultConsumer(repository);
        Acknowledgment acknowledgment = mock(Acknowledgment.class);

        FraudAlertEvent event = FraudAlertEvent.builder()
                .eventId("evt-1")
                .transactionId("tx-123")
                .tenantId("tenant-a")
                .fraudScore(0.97)
                .riskLevel("CRITICAL")
                .recommendation("BLOCK_TRANSACTION")
                .detectedAt(LocalDateTime.now())
                .build();

        consumer.consume(event, 0, 42L, acknowledgment);

        assertEquals(TransactionStatus.BLOCKED, tx.getStatus());
        assertEquals("Fraud engine escalated to BLOCKED: BLOCK_TRANSACTION", tx.getRejectionReason());
        verify(repository).save(tx);
        verify(acknowledgment).acknowledge();
    }

    @Test
    void keepsTransactionOnFraudHoldForManualReview() {
        TransactionRepository repository = mock(TransactionRepository.class);
        Transaction tx = baseTransaction();
        when(repository.findByTransactionId("tx-456")).thenReturn(Optional.of(tx));
        when(repository.save(any(Transaction.class))).thenAnswer(invocation -> invocation.getArgument(0));

        FraudResultConsumer consumer = new FraudResultConsumer(repository);
        Acknowledgment acknowledgment = mock(Acknowledgment.class);

        FraudAlertEvent event = FraudAlertEvent.builder()
                .eventId("evt-2")
                .transactionId("tx-456")
                .tenantId("tenant-a")
                .fraudScore(0.72)
                .riskLevel("HIGH")
                .recommendation("MANUAL_REVIEW")
                .detectedAt(LocalDateTime.now())
                .build();

        consumer.consume(event, 0, 43L, acknowledgment);

        assertEquals(TransactionStatus.FRAUD_HOLD, tx.getStatus());
        verify(repository).save(tx);
        verify(acknowledgment).acknowledge();
    }

    private Transaction baseTransaction() {
        return Transaction.builder()
                .transactionId("tx-123")
                .tenantId("tenant-a")
                .fromAccount("ACC-1")
                .toAccount("ACC-2")
                .amount(BigDecimal.valueOf(100))
                .currency("USD")
                .type(com.bbss.transaction.domain.model.TransactionType.TRANSFER)
                .status(TransactionStatus.SUBMITTED)
                .build();
    }
}