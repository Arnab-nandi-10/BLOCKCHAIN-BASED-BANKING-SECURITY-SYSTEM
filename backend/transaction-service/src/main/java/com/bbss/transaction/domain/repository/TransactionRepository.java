package com.bbss.transaction.domain.repository;

import com.bbss.transaction.domain.model.Transaction;
import com.bbss.transaction.domain.model.TransactionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    /**
     * Find a transaction by its business transaction ID.
     *
     * @param txId the business transaction identifier
     * @return optional transaction
     */
    Optional<Transaction> findByTransactionId(String txId);

    /**
     * List all transactions for a given tenant, ordered newest first.
     *
     * @param tenantId the tenant identifier
     * @param pageable pagination parameters
     * @return page of transactions
     */
    Page<Transaction> findByTenantIdOrderByCreatedAtDesc(String tenantId, Pageable pageable);

    /**
     * List transactions for a tenant filtered by status.
     *
     * @param tenantId the tenant identifier
     * @param status   the transaction status filter
     * @param pageable pagination parameters
     * @return page of transactions
     */
    Page<Transaction> findByTenantIdAndStatus(String tenantId, TransactionStatus status, Pageable pageable);

    /**
     * List transactions for a tenant by originating account.
     *
     * @param tenantId    the tenant identifier
     * @param fromAccount the source account identifier
     * @param pageable    pagination parameters
     * @return page of transactions
     */
    Page<Transaction> findByTenantIdAndFromAccount(String tenantId, String fromAccount, Pageable pageable);

    /**
     * Count transactions for a tenant in a given status within a time range.
     *
     * @param tenantId  the tenant identifier
     * @param status    the transaction status
     * @param startTime range start (inclusive)
     * @param endTime   range end (inclusive)
     * @return count of matching transactions
     */
    long countByTenantIdAndStatusAndCreatedAtBetween(
            String tenantId,
            TransactionStatus status,
            LocalDateTime startTime,
            LocalDateTime endTime
    );
}
