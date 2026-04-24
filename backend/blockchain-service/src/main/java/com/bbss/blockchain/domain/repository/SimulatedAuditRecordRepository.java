package com.bbss.blockchain.domain.repository;

import com.bbss.blockchain.domain.model.SimulatedAuditRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface SimulatedAuditRecordRepository extends JpaRepository<SimulatedAuditRecord, UUID> {

    Optional<SimulatedAuditRecord> findByAuditId(String auditId);

    Optional<SimulatedAuditRecord> findTopByTenantIdOrderByCreatedAtDesc(String tenantId);
}
