package com.bbss.tenant.domain.repository;

import com.bbss.tenant.domain.model.Tenant;
import com.bbss.tenant.domain.model.TenantStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TenantRepository extends JpaRepository<Tenant, UUID> {

    Optional<Tenant> findByTenantId(String tenantId);

    Optional<Tenant> findByApiKey(String apiKey);

    boolean existsByTenantId(String tenantId);

    List<Tenant> findByStatus(TenantStatus status);

    List<Tenant> findByStatusNot(TenantStatus status);
}
