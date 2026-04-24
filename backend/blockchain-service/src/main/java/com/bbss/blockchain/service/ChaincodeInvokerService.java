package com.bbss.blockchain.service;

import com.bbss.blockchain.domain.model.BlockchainRecord;
import com.bbss.blockchain.domain.repository.BlockchainRecordRepository;
import com.bbss.blockchain.dto.BlockchainAuditRequest;
import com.bbss.blockchain.dto.BlockchainAuditResponse;
import com.bbss.blockchain.dto.BlockchainSubmitRequest;
import com.bbss.blockchain.dto.BlockchainSubmitResponse;
import com.bbss.blockchain.dto.BlockchainTransactionResponse;
import com.bbss.blockchain.dto.BlockchainVerificationResponse;
import com.bbss.blockchain.messaging.BlockchainEventPublisher;
import com.bbss.shared.events.AuditEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * High-level service that orchestrates chaincode interactions for transactions and audit entries.
 */
@ConditionalOnProperty(name = "fabric.enabled", havingValue = "true", matchIfMissing = false)
@Service
@RequiredArgsConstructor
@Slf4j
public class ChaincodeInvokerService {

    private static final String TRANSACTION_CC = "transaction-cc";
    private static final String AUDIT_CC = "audit-cc";

    private static final String LEDGER_STATUS_PENDING = "PENDING_LEDGER";
    private static final String LEDGER_STATUS_COMMITTED = "COMMITTED";

    private static final String VERIFICATION_NOT_VERIFIED = "NOT_VERIFIED";
    private static final String VERIFICATION_VERIFIED = "VERIFIED";
    private static final String VERIFICATION_HASH_MISMATCH = "HASH_MISMATCH";
    private static final String VERIFICATION_UNAVAILABLE = "UNAVAILABLE";

    private final FabricGatewayService fabricGatewayService;
    private final BlockchainRecordRepository blockchainRecordRepository;
    private final BlockchainEventPublisher blockchainEventPublisher;
    private final ObjectMapper objectMapper;

    @Transactional
    public BlockchainSubmitResponse submitTransactionToLedger(BlockchainSubmitRequest request) {
        log.info("Submitting transaction to ledger: txId={} tenant={} status={}",
                request.transactionId(), request.tenantId(), request.status());

        BlockchainRecord record = blockchainRecordRepository.findByTransactionId(request.transactionId())
                .orElseGet(() -> BlockchainRecord.builder()
                        .transactionId(request.transactionId())
                        .tenantId(request.tenantId())
                        .chaincodeId(TRANSACTION_CC)
                        .build());

        record.setTenantId(request.tenantId());
        record.setChaincodeId(TRANSACTION_CC);
        record.setStatus(request.status());
        record.setLedgerStatus(LEDGER_STATUS_PENDING);
        record.setVerificationStatus(VERIFICATION_NOT_VERIFIED);
        record.setPayload(buildTransactionChaincodeInput(request, resolvePreviousHash(request.tenantId())));

        blockchainRecordRepository.save(record);
        return attemptTransactionCommit(record);
    }

    @Transactional(readOnly = true)
    public BlockchainTransactionResponse getTransactionFromLedger(String txId) {
        return blockchainRecordRepository.findByTransactionId(txId)
                .map(this::mapRecordToResponse)
                .orElseGet(() -> fetchFromFabric(txId));
    }

    @Transactional
    public BlockchainVerificationResponse verifyTransactionIntegrity(String txId) {
        BlockchainVerificationResponse response = doVerifyTransactionIntegrity(txId, false);
        updateVerificationCache(txId, response);
        blockchainRecordRepository.findByTransactionId(txId).ifPresent(record ->
                blockchainEventPublisher.publishVerificationUpdated(
                        "TRANSACTION",
                        txId,
                        record.getTenantId(),
                        response.verificationStatus(),
                        response.valid(),
                        response.payloadHash(),
                        response.recordHash(),
                        response.previousHash(),
                        response.verifiedAt()
                ));
        return response;
    }

    public BlockchainAuditResponse submitAuditEntry(AuditEvent event) {
        log.info("Submitting audit entry to ledger: auditId={} entityId={} action={}",
                event.getEventId(), event.getEntityId(), event.getAction());

        try {
            String chaincodeInput = buildAuditChaincodeInput(event);
            FabricGatewayService.FabricSubmitResult submitResult = fabricGatewayService.submitTransaction(
                    AUDIT_CC,
                    "CreateAudit",
                    chaincodeInput
            );

            if (!submitResult.committed()) {
                return new BlockchainAuditResponse(
                        event.getEventId(),
                        null,
                        null,
                        VERIFICATION_UNAVAILABLE,
                        false
                );
            }

            BlockchainVerificationResponse verification = doVerifyAuditIntegrity(event.getEventId(), true);

            return new BlockchainAuditResponse(
                    event.getEventId(),
                    submitResult.transactionId(),
                    submitResult.blockNumber(),
                    verification.verificationStatus(),
                    true
            );
        } catch (Exception ex) {
            log.error("Failed to submit audit entry {} to Fabric: {}", event.getEventId(), ex.getMessage(), ex);
            throw new FabricGatewayService.BlockchainServiceException(
                    "Audit ledger submission failed: " + ex.getMessage(), ex);
        }
    }

    public BlockchainAuditResponse submitAuditEntryFromRequest(BlockchainAuditRequest request) {
        AuditEvent event = new AuditEvent();
        event.setEventId(request.auditId());
        event.setTenantId(request.tenantId());
        event.setEntityType(request.entityType());
        event.setEntityId(request.entityId());
        event.setAction(request.action());
        event.setActorId(request.actorId());
        event.setActorType("SYSTEM");
        event.setPayload(request.payload());
        event.setOccurredAt(request.occurredAt());
        return submitAuditEntry(event);
    }

    @Transactional(readOnly = true)
    public JsonNode getAuditRecord(String auditId) {
        byte[] response = fabricGatewayService.evaluateTransaction(AUDIT_CC, "QueryRecord", auditId);
        JsonNode node = fabricGatewayService.parseJsonResponse(response);
        if (node.isEmpty()) {
            throw new EntityNotFoundException("Audit record not found on ledger: " + auditId);
        }
        return node;
    }

    public BlockchainVerificationResponse verifyAuditIntegrity(String auditId) {
        BlockchainVerificationResponse response = doVerifyAuditIntegrity(auditId, false);
        String tenantId = null;
        try {
            tenantId = textValue(getAuditRecord(auditId), "tenantId");
        } catch (Exception ex) {
            log.debug("Could not resolve tenantId while publishing audit verification update for {}: {}",
                    auditId, ex.getMessage());
        }
        blockchainEventPublisher.publishVerificationUpdated(
                "AUDIT",
                auditId,
                tenantId,
                response.verificationStatus(),
                response.valid(),
                response.payloadHash(),
                response.recordHash(),
                response.previousHash(),
                response.verifiedAt()
        );
        return response;
    }

    @Transactional(readOnly = true)
    public JsonNode getTransactionHistory(String txId) {
        byte[] response = fabricGatewayService.evaluateTransaction(TRANSACTION_CC, "GetTransactionHistory", txId);
        return fabricGatewayService.parseJsonResponse(response);
    }

    @Transactional(readOnly = true)
    public JsonNode getAuditHistory(String auditId) {
        byte[] response = fabricGatewayService.evaluateTransaction(AUDIT_CC, "GetAuditHistory", auditId);
        return fabricGatewayService.parseJsonResponse(response);
    }

    @Transactional(readOnly = true)
    public JsonNode listAuditRecords(
            String tenantId,
            String entityType,
            String entityId,
            String action,
            LocalDateTime fromDate,
            LocalDateTime toDate) {

        JsonNode source = entityType != null && entityId != null
                ? fabricGatewayService.parseJsonResponse(
                        fabricGatewayService.evaluateTransaction(
                                AUDIT_CC,
                                "QueryAuditByEntity",
                                tenantId,
                                entityType,
                                entityId
                        ))
                : fabricGatewayService.parseJsonResponse(
                        fabricGatewayService.evaluateTransaction(AUDIT_CC, "QueryAuditByTenant", tenantId));

        ArrayNode filtered = objectMapper.createArrayNode();
        if (!source.isArray()) {
            return filtered;
        }

        for (JsonNode node : source) {
            if (!matchesAuditFilters(node, action, fromDate, toDate)) {
                continue;
            }
            filtered.add(node);
        }
        return filtered;
    }

    @Scheduled(fixedDelay = 60_000L)
    @Transactional
    public void retryPendingTransactions() {
        List<BlockchainRecord> pendingRecords =
                blockchainRecordRepository.findTop50ByLedgerStatusAndNextRetryAtLessThanEqualOrderByCreatedAtAsc(
                        LEDGER_STATUS_PENDING,
                        LocalDateTime.now()
                );

        if (pendingRecords.isEmpty()) {
            return;
        }

        log.info("Retrying {} pending blockchain records", pendingRecords.size());
        pendingRecords.forEach(this::attemptTransactionCommit);
    }

    private BlockchainSubmitResponse attemptTransactionCommit(BlockchainRecord record) {
        try {
            FabricGatewayService.FabricSubmitResult submitResult = fabricGatewayService.submitTransaction(
                    TRANSACTION_CC,
                    "CreateRecord",
                    record.getPayload()
            );

            if (!submitResult.committed()) {
                return markPending(record, "Fabric gateway is unavailable; retry scheduled");
            }

            JsonNode responseNode = fabricGatewayService.parseJsonResponse(submitResult.result());
            BlockchainVerificationResponse verification =
                    doVerifyTransactionIntegrity(record.getTransactionId(), true);

            record.setBlockchainTxId(firstNonBlank(
                    submitResult.transactionId(),
                    textValue(responseNode, "blockchainTxId"))
            );
            record.setBlockNumber(firstNonBlank(
                    submitResult.blockNumber(),
                    textValue(responseNode, "blockNumber"))
            );
            record.setPayloadHash(textValue(responseNode, "payloadHash"));
            record.setRecordHash(textValue(responseNode, "recordHash"));
            record.setPreviousHash(textValue(responseNode, "previousHash"));
            record.setLedgerStatus(LEDGER_STATUS_COMMITTED);
            record.setVerificationStatus(verification.verificationStatus());
            record.setLastError(null);
            record.setRetryCount(0);
            record.setNextRetryAt(null);

            BlockchainRecord saved = blockchainRecordRepository.save(record);

            blockchainEventPublisher.publishBlockCommitted(
                    saved.getTransactionId(),
                    saved.getBlockNumber(),
                    saved.getBlockchainTxId(),
                    saved.getTenantId(),
                    saved.getVerificationStatus(),
                    saved.getPayloadHash(),
                    saved.getRecordHash(),
                    saved.getPreviousHash()
            );

            return new BlockchainSubmitResponse(
                    saved.getTransactionId(),
                    saved.getBlockchainTxId(),
                    saved.getBlockNumber(),
                    saved.getLedgerStatus(),
                    saved.getVerificationStatus(),
                    saved.getPayloadHash(),
                    saved.getRecordHash(),
                    saved.getPreviousHash(),
                    true,
                    "Transaction committed to Fabric successfully"
            );
        } catch (Exception ex) {
            return markPending(record, ex.getMessage());
        }
    }

    private BlockchainSubmitResponse markPending(BlockchainRecord record, String message) {
        int attempt = record.getRetryCount() + 1;

        record.setLedgerStatus(LEDGER_STATUS_PENDING);
        record.setVerificationStatus(VERIFICATION_UNAVAILABLE);
        record.setLastError(truncate(message, 1024));
        record.setRetryCount(attempt);
        record.setNextRetryAt(LocalDateTime.now().plusSeconds(backoffSeconds(attempt)));

        BlockchainRecord saved = blockchainRecordRepository.save(record);

        blockchainEventPublisher.publishBlockCommitFailed(
                saved.getTransactionId(),
                saved.getTenantId(),
                saved.getLastError()
        );

        log.warn("Transaction {} remains pending for ledger retry. attempt={} nextRetryAt={} reason={}",
                saved.getTransactionId(), saved.getRetryCount(), saved.getNextRetryAt(), saved.getLastError());

        return new BlockchainSubmitResponse(
                saved.getTransactionId(),
                saved.getBlockchainTxId(),
                saved.getBlockNumber(),
                saved.getLedgerStatus(),
                saved.getVerificationStatus(),
                saved.getPayloadHash(),
                saved.getRecordHash(),
                saved.getPreviousHash(),
                false,
                saved.getLastError()
        );
    }

    private BlockchainTransactionResponse fetchFromFabric(String txId) {
        log.info("Cache miss — querying Fabric ledger for txId={}", txId);
        try {
            byte[] response = fabricGatewayService.evaluateTransaction(
                    TRANSACTION_CC,
                    "QueryRecord",
                    txId
            );
            JsonNode node = fabricGatewayService.parseJsonResponse(response);
            if (node.isEmpty()) {
                throw new EntityNotFoundException("Transaction not found on ledger: " + txId);
            }

            BlockchainVerificationResponse verification = doVerifyTransactionIntegrity(txId, true);

            return new BlockchainTransactionResponse(
                    node.path("transactionId").asText(txId),
                    node.path("blockchainTxId").asText(null),
                    null,
                    LEDGER_STATUS_COMMITTED,
                    verification.verificationStatus(),
                    node.path("payloadHash").asText(null),
                    node.path("recordHash").asText(null),
                    node.path("previousHash").asText(null),
                    node.path("status").asText(null),
                    parseTimestamp(node.path("decisionTimestamp").asText(null))
            );
        } catch (Exception ex) {
            log.error("Fabric ledger query failed for txId={}: {}", txId, ex.getMessage());
            throw new EntityNotFoundException("Transaction not found on ledger: " + txId);
        }
    }

    private BlockchainTransactionResponse mapRecordToResponse(BlockchainRecord record) {
        return new BlockchainTransactionResponse(
                record.getTransactionId(),
                record.getBlockchainTxId(),
                record.getBlockNumber(),
                record.getLedgerStatus(),
                record.getVerificationStatus(),
                record.getPayloadHash(),
                record.getRecordHash(),
                record.getPreviousHash(),
                record.getStatus(),
                record.getUpdatedAt() != null ? record.getUpdatedAt() : record.getCreatedAt()
        );
    }

    private BlockchainVerificationResponse doVerifyTransactionIntegrity(String txId, boolean suppressErrors) {
        try {
            byte[] response = fabricGatewayService.evaluateTransaction(
                    TRANSACTION_CC,
                    "VerifyIntegrity",
                    txId
            );
            JsonNode node = fabricGatewayService.parseJsonResponse(response);
            return toVerificationResponse(node);
        } catch (Exception ex) {
            if (!suppressErrors) {
                throw new FabricGatewayService.BlockchainServiceException(
                        "Failed to verify transaction integrity: " + ex.getMessage(), ex);
            }
            return unavailableVerification(txId);
        }
    }

    private BlockchainVerificationResponse doVerifyAuditIntegrity(String auditId, boolean suppressErrors) {
        try {
            byte[] response = fabricGatewayService.evaluateTransaction(
                    AUDIT_CC,
                    "VerifyIntegrity",
                    auditId
            );
            JsonNode node = fabricGatewayService.parseJsonResponse(response);
            return toVerificationResponse(node);
        } catch (Exception ex) {
            if (!suppressErrors) {
                throw new FabricGatewayService.BlockchainServiceException(
                        "Failed to verify audit integrity: " + ex.getMessage(), ex);
            }
            return unavailableVerification(auditId);
        }
    }

    private BlockchainVerificationResponse toVerificationResponse(JsonNode node) {
        boolean valid = node.path("valid").asBoolean(false);
        return new BlockchainVerificationResponse(
                node.path("recordId").asText(null),
                valid ? VERIFICATION_VERIFIED : VERIFICATION_HASH_MISMATCH,
                valid,
                node.path("payloadHash").asText(null),
                node.path("recomputedPayloadHash").asText(null),
                node.path("recordHash").asText(null),
                node.path("recomputedRecordHash").asText(null),
                node.path("previousHash").asText(null),
                node.path("verifiedAt").asText(null)
        );
    }

    private BlockchainVerificationResponse unavailableVerification(String recordId) {
        return new BlockchainVerificationResponse(
                recordId,
                VERIFICATION_UNAVAILABLE,
                false,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }

    private void updateVerificationCache(String txId, BlockchainVerificationResponse response) {
        blockchainRecordRepository.findByTransactionId(txId).ifPresent(record -> {
            record.setVerificationStatus(response.verificationStatus());
            if (response.payloadHash() != null) {
                record.setPayloadHash(response.payloadHash());
            }
            if (response.recordHash() != null) {
                record.setRecordHash(response.recordHash());
            }
            if (response.previousHash() != null) {
                record.setPreviousHash(response.previousHash());
            }
            blockchainRecordRepository.save(record);
        });
    }

    private String buildTransactionChaincodeInput(BlockchainSubmitRequest request, String previousHash) {
        try {
            String payload = objectMapper.writeValueAsString(buildSanitisedTransactionPayload(request));

            Map<String, Object> chaincodeInput = new LinkedHashMap<>();
            chaincodeInput.put("transactionId", request.transactionId());
            chaincodeInput.put("tenantId", request.tenantId());
            chaincodeInput.put("fromAccountMasked", maskAccount(request.fromAccount()));
            chaincodeInput.put("toAccountMasked", maskAccount(request.toAccount()));
            chaincodeInput.put("amount", request.amount() != null ? request.amount().toPlainString() : null);
            chaincodeInput.put("currency", request.currency());
            chaincodeInput.put("transactionType", request.type());
            chaincodeInput.put("status", request.status());
            chaincodeInput.put("fraudScore", request.fraudScore() != null ? request.fraudScore().toString() : null);
            chaincodeInput.put("riskLevel", request.riskLevel());
            chaincodeInput.put("decision", firstNonBlank(request.decision(), deriveDecision(request.status())));
            chaincodeInput.put("decisionReason", request.decisionReason());
            chaincodeInput.put("explanation", request.decisionReason());
            chaincodeInput.put("decisionTimestamp", request.timestamp() != null ? request.timestamp().toString() : null);
            chaincodeInput.put("previousHash", previousHash);
            chaincodeInput.put("payload", payload);

            return objectMapper.writeValueAsString(chaincodeInput);
        } catch (JsonProcessingException ex) {
            throw new FabricGatewayService.BlockchainServiceException(
                    "Failed to build transaction chaincode payload: " + ex.getMessage(), ex);
        }
    }

    private Map<String, Object> buildSanitisedTransactionPayload(BlockchainSubmitRequest request) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("transactionId", request.transactionId());
        payload.put("tenantId", request.tenantId());
        payload.put("fromAccountMasked", maskAccount(request.fromAccount()));
        payload.put("toAccountMasked", maskAccount(request.toAccount()));
        payload.put("amount", request.amount() != null ? request.amount().toPlainString() : null);
        payload.put("currency", request.currency());
        payload.put("type", request.type());
        payload.put("status", request.status());
        payload.put("fraudScore", request.fraudScore());
        payload.put("riskLevel", request.riskLevel());
        payload.put("decision", firstNonBlank(request.decision(), deriveDecision(request.status())));
        payload.put("decisionReason", request.decisionReason());
        payload.put("timestamp", request.timestamp());
        payload.put("ipAddressHash", hashValue(request.ipAddress()));
        if (request.metadata() != null && !request.metadata().isEmpty()) {
            payload.put("metadataHash", hashJson(request.metadata()));
        }
        return payload;
    }

    private String buildAuditChaincodeInput(AuditEvent event) {
        try {
            JsonNode payloadNode = toJsonNode(event.getPayload());
            String transactionId = firstNonBlank(
                    extractText(payloadNode, "transactionId", "entityId"),
                    "TRANSACTION".equalsIgnoreCase(event.getEntityType()) ? event.getEntityId() : null
            );
            String fraudScore = extractText(payloadNode, "fraudScore", "score");
            String riskLevel = extractText(payloadNode, "riskLevel");
            String decision = extractText(payloadNode, "decision", "recommendation");

            Map<String, Object> sanitisedPayload = new LinkedHashMap<>();
            sanitisedPayload.put("auditId", event.getEventId());
            sanitisedPayload.put("entityType", event.getEntityType());
            sanitisedPayload.put("entityId", event.getEntityId());
            sanitisedPayload.put("action", event.getAction());
            sanitisedPayload.put("actorId", event.getActorId());
            sanitisedPayload.put("actorType", firstNonBlank(event.getActorType(), "SYSTEM"));
            sanitisedPayload.put("transactionId", transactionId);
            sanitisedPayload.put("fraudScore", fraudScore);
            sanitisedPayload.put("riskLevel", riskLevel);
            sanitisedPayload.put("decision", decision);
            sanitisedPayload.put("occurredAt", event.getOccurredAt() != null ? event.getOccurredAt().toString() : null);
            sanitisedPayload.put("payloadHash", hashJson(payloadNode));

            String payload = objectMapper.writeValueAsString(sanitisedPayload);

            Map<String, Object> input = new LinkedHashMap<>();
            input.put("auditId", event.getEventId());
            input.put("tenantId", event.getTenantId());
            input.put("entityType", event.getEntityType());
            input.put("entityId", event.getEntityId());
            input.put("action", event.getAction());
            input.put("actorId", event.getActorId());
            input.put("actorType", firstNonBlank(event.getActorType(), "SYSTEM"));
            input.put("ipAddressHash", hashValue(event.getIpAddress()));
            input.put("transactionId", transactionId);
            input.put("fraudScore", fraudScore);
            input.put("riskLevel", riskLevel);
            input.put("decision", decision);
            input.put("explanation", event.getAction());
            input.put("occurredAt", event.getOccurredAt() != null ? event.getOccurredAt().toString() : null);
            input.put("payload", payload);

            return objectMapper.writeValueAsString(input);
        } catch (JsonProcessingException ex) {
            throw new FabricGatewayService.BlockchainServiceException(
                    "Failed to build audit chaincode payload: " + ex.getMessage(), ex);
        }
    }

    private JsonNode toJsonNode(Object value) {
        if (value == null) {
            return objectMapper.createObjectNode();
        }
        if (value instanceof JsonNode node) {
            return node;
        }
        if (value instanceof String raw) {
            try {
                return objectMapper.readTree(raw);
            } catch (Exception ex) {
                ObjectNode node = objectMapper.createObjectNode();
                node.put("rawPayload", raw);
                return node;
            }
        }
        return objectMapper.valueToTree(value);
    }

    private String resolvePreviousHash(String tenantId) {
        return blockchainRecordRepository.findTopByTenantIdAndLedgerStatusOrderByCreatedAtDesc(
                        tenantId,
                        LEDGER_STATUS_COMMITTED
                )
                .map(BlockchainRecord::getRecordHash)
                .orElse(null);
    }

    private long backoffSeconds(int attempt) {
        return Math.min(900L, 30L * (1L << Math.min(attempt, 4)));
    }

    private LocalDateTime parseTimestamp(String ts) {
        if (ts == null || ts.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(ts);
        } catch (Exception ignored) {
            try {
                return OffsetDateTime.parse(ts, DateTimeFormatter.ISO_OFFSET_DATE_TIME).toLocalDateTime();
            } catch (Exception ex) {
                log.warn("Could not parse ledger timestamp '{}': {}", ts, ex.getMessage());
                return null;
            }
        }
    }

    private String textValue(JsonNode node, String field) {
        String value = node.path(field).asText(null);
        return value != null && !value.isBlank() ? value : null;
    }

    private String extractText(JsonNode node, String... fieldNames) {
        for (String fieldName : fieldNames) {
            JsonNode candidate = node.path(fieldName);
            if (!candidate.isMissingNode() && !candidate.isNull()) {
                String text = candidate.asText();
                if (!text.isBlank()) {
                    return text;
                }
            }
        }
        return null;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private String deriveDecision(String status) {
        if (status == null) {
            return null;
        }
        return switch (status) {
            case "BLOCKED" -> "BLOCK";
            case "FRAUD_HOLD" -> "HOLD";
            case "VERIFIED", "COMPLETED" -> "ALLOW";
            default -> null;
        };
    }

    private String maskAccount(String account) {
        if (account == null || account.isBlank()) {
            return null;
        }
        String trimmed = account.trim();
        if (trimmed.length() <= 4) {
            return "*".repeat(trimmed.length());
        }
        return "*".repeat(trimmed.length() - 4) + trimmed.substring(trimmed.length() - 4);
    }

    private String hashValue(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.trim().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception ex) {
            throw new FabricGatewayService.BlockchainServiceException(
                    "Failed to hash sensitive value: " + ex.getMessage(), ex);
        }
    }

    private String hashJson(Object value) {
        try {
            return hashValue(objectMapper.writeValueAsString(value));
        } catch (JsonProcessingException ex) {
            throw new FabricGatewayService.BlockchainServiceException(
                    "Failed to hash JSON payload: " + ex.getMessage(), ex);
        }
    }

    private String truncate(String value, int limit) {
        if (value == null || value.length() <= limit) {
            return value;
        }
        return value.substring(0, limit);
    }

    private boolean matchesAuditFilters(
            JsonNode node,
            String action,
            LocalDateTime fromDate,
            LocalDateTime toDate) {
        if (action != null && !action.isBlank()) {
            String candidate = textValue(node, "action");
            if (candidate == null || !candidate.equalsIgnoreCase(action)) {
                return false;
            }
        }

        if (fromDate == null && toDate == null) {
            return true;
        }

        LocalDateTime occurredAt = parseTimestamp(textValue(node, "occurredAt"));
        if (occurredAt == null) {
            return false;
        }
        if (fromDate != null && occurredAt.isBefore(fromDate)) {
            return false;
        }
        if (toDate != null && occurredAt.isAfter(toDate)) {
            return false;
        }
        return true;
    }
}
