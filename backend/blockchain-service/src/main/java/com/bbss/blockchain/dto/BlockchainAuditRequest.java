package com.bbss.blockchain.dto;

import java.time.LocalDateTime;

/**
 * Payload received from the audit-service when anchoring an audit record
 * to the Hyperledger Fabric ledger.
 *
 * @param auditId     business-level audit identifier for deduplication
 * @param tenantId    owning tenant
 * @param entityType  domain category (e.g. TRANSACTION, USER, TENANT)
 * @param entityId    identifier of the affected entity
 * @param action      event verb (e.g. TRANSACTION_VERIFIED, FRAUD_DETECTED)
 * @param actorId     identifier of the principal that triggered the event
 * @param payload     full JSON snapshot of the originating event
 * @param occurredAt  server-side instant at which the event occurred
 */
public record BlockchainAuditRequest(
        String        auditId,
        String        tenantId,
        String        entityType,
        String        entityId,
        String        action,
        String        actorId,
        String        payload,
        LocalDateTime occurredAt
) {}
