package com.bbss.blockchain.domain.repository;

import com.bbss.blockchain.domain.model.BlockchainRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for the local blockchain record cache.
 */
@Repository
public interface BlockchainRecordRepository extends JpaRepository<BlockchainRecord, UUID> {

    /**
     * Find a cached blockchain record by its business transaction ID.
     *
     * @param transactionId the business transaction identifier
     * @return optional blockchain record
     */
    Optional<BlockchainRecord> findByTransactionId(String transactionId);

    /**
     * List all cached blockchain records for a tenant, newest first.
     *
     * @param tenantId the tenant identifier
     * @param pageable pagination parameters
     * @return page of blockchain records
     */
    Page<BlockchainRecord> findByTenantIdOrderByCreatedAtDesc(String tenantId, Pageable pageable);

    Optional<BlockchainRecord> findTopByTenantIdAndLedgerStatusOrderByCreatedAtDesc(
            String tenantId,
            String ledgerStatus);

    List<BlockchainRecord> findTop50ByLedgerStatusAndNextRetryAtLessThanEqualOrderByCreatedAtAsc(
            String ledgerStatus,
            LocalDateTime nextRetryAt);
}
