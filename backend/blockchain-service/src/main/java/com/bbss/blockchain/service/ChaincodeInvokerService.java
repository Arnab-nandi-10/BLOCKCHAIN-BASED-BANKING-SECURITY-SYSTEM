package com.bbss.blockchain.service;

import com.bbss.shared.events.AuditEvent;
import com.bbss.blockchain.domain.model.BlockchainRecord;
import com.bbss.blockchain.domain.repository.BlockchainRecordRepository;
import com.bbss.blockchain.dto.BlockchainAuditRequest;
import com.bbss.blockchain.dto.BlockchainSubmitRequest;
import com.bbss.blockchain.dto.BlockchainSubmitResponse;
import com.bbss.blockchain.dto.BlockchainTransactionResponse;
import com.bbss.blockchain.messaging.BlockchainEventPublisher;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * High-level service that orchestrates chaincode interactions for transactions
 * and audit entries.
 *
 * <p>All ledger writes are cached in the local PostgreSQL table
 * {@code blockchain_records} so that subsequent reads can be served without
 * round-tripping to the Fabric peer.</p>
 */

@ConditionalOnProperty(name = "fabric.enabled", havingValue = "true", matchIfMissing = false)
@Service
@RequiredArgsConstructor
@Slf4j
public class ChaincodeInvokerService {

    private static final String TRANSACTION_CC = "transaction-cc";
    private static final String AUDIT_CC        = "audit-cc";

    private final FabricGatewayService    fabricGatewayService;
    private final BlockchainRecordRepository blockchainRecordRepository;
    private final BlockchainEventPublisher   blockchainEventPublisher;
    private final ObjectMapper               objectMapper;

    // -------------------------------------------------------------------------
    // Transaction ledger operations
    // -------------------------------------------------------------------------

    /**
     * Write a transaction to the Hyperledger Fabric ledger and cache the result locally.
     *
     * @param request the transaction submission payload
     * @return blockchain submission result containing the Fabric tx hash and block number
     */
    @Transactional
    public BlockchainSubmitResponse submitTransactionToLedger(BlockchainSubmitRequest request) {
        log.info("Submitting transaction to ledger: txId={} tenant={}",
                request.transactionId(), request.tenantId());

        // 1. Serialise the full payload to JSON for the chaincode argument.
        String payloadJson;
        try {
            payloadJson = objectMapper.writeValueAsString(request);
        } catch (JsonProcessingException ex) {
            log.error("Failed to serialise blockchain submit request: {}", ex.getMessage());
            return new BlockchainSubmitResponse(
                    request.transactionId(), null, null, false,
                    "Payload serialisation failed: " + ex.getMessage());
        }

        // 2. Invoke the chaincode CreateTransaction function.
        byte[] response;
        try {
            response = fabricGatewayService.submitTransaction(
                    TRANSACTION_CC,
                    "CreateTransaction",
                    request.transactionId(),
                    request.tenantId(),
                    payloadJson
            );
        } catch (FabricGatewayService.BlockchainServiceException ex) {
            log.error("Ledger submission failed for txId={}: {}",
                    request.transactionId(), ex.getMessage());
            return new BlockchainSubmitResponse(
                    request.transactionId(), null, null, false,
                    "Ledger submission failed: " + ex.getMessage());
        }

        // 3. Parse the chaincode response for blockchainTxId and blockNumber.
        JsonNode responseNode = fabricGatewayService.parseJsonResponse(response);
        String blockchainTxId = responseNode.path("blockchainTxId").asText(null);
        String blockNumber    = responseNode.path("blockNumber").asText(null);

        // 4. Persist a local cache entry.
        BlockchainRecord record = BlockchainRecord.builder()
                .transactionId(request.transactionId())
                .tenantId(request.tenantId())
                .blockchainTxId(blockchainTxId)
                .blockNumber(blockNumber)
                .chaincodeId(TRANSACTION_CC)
                .payload(payloadJson)
                .status(request.status())
                .build();
        blockchainRecordRepository.save(record);
        log.info("BlockchainRecord cached: txId={} blockchainTxId={} blockNumber={}",
                request.transactionId(), blockchainTxId, blockNumber);

        // 5. Publish block-committed event.
        blockchainEventPublisher.publishBlockCommitted(blockNumber, blockchainTxId, request.tenantId());

        return new BlockchainSubmitResponse(
                request.transactionId(),
                blockchainTxId,
                blockNumber,
                true,
                "Transaction committed to ledger successfully"
        );
    }

    /**
     * Retrieve an on-chain transaction record.
     *
     * <p>Checks the local cache first. Falls back to a Fabric ledger query if the
     * record is not cached.</p>
     *
     * @param txId the business transaction ID
     * @return on-chain transaction response
     */
    @Transactional(readOnly = true)
    public BlockchainTransactionResponse getTransactionFromLedger(String txId) {
        log.debug("Retrieving transaction from ledger/cache: txId={}", txId);

        // 1. Check local cache.
        return blockchainRecordRepository.findByTransactionId(txId)
                .map(this::mapRecordToResponse)
                .orElseGet(() -> fetchFromFabric(txId));
    }

    /**
     * Write an audit entry to the Hyperledger Fabric audit chaincode.
     *
     * @param event the audit event to persist on-chain
     */
    public void submitAuditEntry(AuditEvent event) {
        log.info("Submitting audit entry to ledger: eventId={} entityId={} action={}",
                event.getEventId(), event.getEntityId(), event.getAction());

        String auditPayload;
        try {
            auditPayload = objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException ex) {
            log.error("Failed to serialise audit event: {}", ex.getMessage());
            throw new FabricGatewayService.BlockchainServiceException(
                    "Audit event serialisation failed: " + ex.getMessage(), ex);
        }

        fabricGatewayService.submitTransaction(
                AUDIT_CC,
                "CreateAuditEntry",
                event.getEventId(),
                event.getTenantId(),
                auditPayload
        );

        log.debug("Audit entry committed to ledger: eventId={}", event.getEventId());
    }

    /**
     * Adapts a {@link BlockchainAuditRequest} received from the REST layer into an
     * {@link AuditEvent} and delegates to {@link #submitAuditEntry(AuditEvent)}.
     *
     * @param request the REST audit request payload
     */
    public void submitAuditEntryFromRequest(BlockchainAuditRequest request) {
        AuditEvent event = new AuditEvent();
        event.setEventId(request.auditId());
        event.setTenantId(request.tenantId());
        event.setEntityType(request.entityType());
        event.setEntityId(request.entityId());
        event.setAction(request.action());
        event.setActorId(request.actorId());
        event.setPayload(request.payload());
        event.setOccurredAt(request.occurredAt());
        submitAuditEntry(event);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Query the Fabric ledger directly for a transaction that is absent from the cache.
     */
    private BlockchainTransactionResponse fetchFromFabric(String txId) {
        log.info("Cache miss — querying Fabric ledger for txId={}", txId);
        try {
            byte[] response = fabricGatewayService.evaluateTransaction(
                    TRANSACTION_CC, "GetTransaction", txId);
            JsonNode node = fabricGatewayService.parseJsonResponse(response);

            return new BlockchainTransactionResponse(
                    node.path("transactionId").asText(txId),
                    node.path("blockchainTxId").asText(null),
                    node.path("blockNumber").asText(null),
                    node.path("status").asText(null),
                    parseTimestamp(node.path("timestamp").asText(null))
            );
        } catch (FabricGatewayService.BlockchainServiceException ex) {
            log.error("Fabric ledger query failed for txId={}: {}", txId, ex.getMessage());
            throw new jakarta.persistence.EntityNotFoundException(
                    "Transaction not found on ledger: " + txId);
        }
    }

    private BlockchainTransactionResponse mapRecordToResponse(BlockchainRecord record) {
        return new BlockchainTransactionResponse(
                record.getTransactionId(),
                record.getBlockchainTxId(),
                record.getBlockNumber(),
                record.getStatus(),
                record.getCreatedAt()
        );
    }

    private LocalDateTime parseTimestamp(String ts) {
        if (ts == null || ts.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(ts);
        } catch (Exception ex) {
            log.warn("Could not parse ledger timestamp '{}': {}", ts, ex.getMessage());
            return null;
        }
    }
}
