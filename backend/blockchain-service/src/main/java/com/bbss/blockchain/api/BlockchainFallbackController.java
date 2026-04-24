package com.bbss.blockchain.api;

import com.bbss.blockchain.domain.model.BlockchainRecord;
import com.bbss.blockchain.domain.model.SimulatedAuditRecord;
import com.bbss.blockchain.domain.repository.BlockchainRecordRepository;
import com.bbss.blockchain.domain.repository.SimulatedAuditRecordRepository;
import com.bbss.blockchain.dto.BlockchainAuditRequest;
import com.bbss.blockchain.dto.BlockchainAuditResponse;
import com.bbss.blockchain.dto.BlockchainSubmitRequest;
import com.bbss.blockchain.dto.BlockchainSubmitResponse;
import com.bbss.blockchain.dto.BlockchainTransactionResponse;
import com.bbss.blockchain.dto.BlockchainVerificationResponse;
import com.bbss.blockchain.messaging.BlockchainEventPublisher;
import com.bbss.shared.dto.ApiResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Fallback REST controller that is active when {@code fabric.enabled=false}.
 *
 * <p>When Hyperledger Fabric is not available, this controller preserves the
 * API contract with deterministic simulated records so the rest of the
 * platform can still demonstrate ledger status, hashes, and verification.</p>
 */
@ConditionalOnProperty(name = "fabric.enabled", havingValue = "false", matchIfMissing = true)
@RestController
@RequestMapping("/api/v1/blockchain")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Blockchain (Simulated)", description = "Simulated blockchain endpoints — Fabric is disabled")
public class BlockchainFallbackController {

    private static final String LEDGER_STATUS_COMMITTED = "COMMITTED";
    private static final String VERIFICATION_VERIFIED = "VERIFIED";
    private static final String VERIFICATION_HASH_MISMATCH = "HASH_MISMATCH";
    private static final String AUDIT_SCHEMA_VERSION = "sim-2.0";

    private final BlockchainRecordRepository blockchainRecordRepository;
    private final SimulatedAuditRecordRepository simulatedAuditRecordRepository;
    private final BlockchainEventPublisher blockchainEventPublisher;
    private final ObjectMapper objectMapper;

    private static long simulatedBlockCounter = 1_000_000L;

    private static synchronized String nextSimBlockNumber() {
        return String.valueOf(simulatedBlockCounter++);
    }

    private static String simTxId() {
        return "sim-" + UUID.randomUUID().toString().replace("-", "");
    }

    @PostMapping("/transactions")
    @Operation(summary = "Submit transaction to ledger (simulated)",
               description = "Persists a local hashed record and returns a simulated blockchain TX ID.")
    public ResponseEntity<ApiResponse<BlockchainSubmitResponse>> submitTransaction(
            @Valid @RequestBody BlockchainSubmitRequest request) {

        log.info("[SIMULATED] Blockchain submit: txId={} tenant={}",
                request.transactionId(), request.tenantId());

        String previousHash = blockchainRecordRepository
                .findTopByTenantIdAndLedgerStatusOrderByCreatedAtDesc(request.tenantId(), LEDGER_STATUS_COMMITTED)
                .map(BlockchainRecord::getRecordHash)
                .orElse(null);

        String payloadJson = canonicalJson(buildSanitisedTransactionPayload(request));
        String payloadHash = sha256Hex(payloadJson);
        String recordHash = sha256Hex(String.join("|",
                "TRANSACTION",
                request.transactionId(),
                request.tenantId(),
                defaultString(request.status(), "UNKNOWN"),
                payloadHash,
                defaultString(previousHash, "")
        ));

        String blockchainTxId = simTxId();
        String blockNumber = nextSimBlockNumber();

        BlockchainRecord record = blockchainRecordRepository.findByTransactionId(request.transactionId())
                .orElseGet(() -> BlockchainRecord.builder()
                        .transactionId(request.transactionId())
                        .tenantId(request.tenantId())
                        .chaincodeId("transaction-cc-simulated")
                        .build());

        record.setTenantId(request.tenantId());
        record.setBlockchainTxId(blockchainTxId);
        record.setBlockNumber(blockNumber);
        record.setChaincodeId("transaction-cc-simulated");
        record.setPayload(payloadJson);
        record.setStatus(request.status());
        record.setLedgerStatus(LEDGER_STATUS_COMMITTED);
        record.setVerificationStatus(VERIFICATION_VERIFIED);
        record.setPayloadHash(payloadHash);
        record.setRecordHash(recordHash);
        record.setPreviousHash(previousHash);
        record.setLastError(null);
        record.setRetryCount(0);
        record.setNextRetryAt(null);
        BlockchainRecord savedRecord = blockchainRecordRepository.save(record);

        blockchainEventPublisher.publishBlockCommitted(
                savedRecord.getTransactionId(),
                savedRecord.getBlockNumber(),
                savedRecord.getBlockchainTxId(),
                savedRecord.getTenantId(),
                savedRecord.getVerificationStatus(),
                savedRecord.getPayloadHash(),
                savedRecord.getRecordHash(),
                savedRecord.getPreviousHash()
        );

        BlockchainSubmitResponse response = new BlockchainSubmitResponse(
                request.transactionId(),
                blockchainTxId,
                blockNumber,
                LEDGER_STATUS_COMMITTED,
                VERIFICATION_VERIFIED,
                payloadHash,
                recordHash,
                previousHash,
                true,
                "Simulated ledger write — Fabric is disabled");

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Transaction recorded (simulated)", response));
    }

    @GetMapping("/transactions/{txId}")
    @Operation(summary = "Get transaction record (simulated)")
    public ResponseEntity<ApiResponse<BlockchainTransactionResponse>> getTransaction(
            @PathVariable String txId) {

        return blockchainRecordRepository.findByTransactionId(txId)
                .map(rec -> {
                    BlockchainTransactionResponse response = new BlockchainTransactionResponse(
                            rec.getTransactionId(),
                            rec.getBlockchainTxId(),
                            rec.getBlockNumber(),
                            rec.getLedgerStatus(),
                            rec.getVerificationStatus(),
                            rec.getPayloadHash(),
                            rec.getRecordHash(),
                            rec.getPreviousHash(),
                            rec.getStatus(),
                            rec.getCreatedAt());
                    return ResponseEntity.ok(ApiResponse.success(response));
                })
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.error("Transaction not found: " + txId)));
    }

    @GetMapping("/transactions/{txId}/verify")
    @Operation(summary = "Verify transaction integrity (simulated)")
    public ResponseEntity<ApiResponse<BlockchainVerificationResponse>> verifyTransaction(
            @PathVariable String txId) {

        return blockchainRecordRepository.findByTransactionId(txId)
                .map(record -> {
                    BlockchainVerificationResponse response = buildTransactionVerification(record);
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
                    );
                    return ResponseEntity.ok(ApiResponse.success(response));
                })
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.error("Transaction not found: " + txId)));
    }

    @GetMapping("/transactions/{txId}/history")
    @Operation(summary = "Get transaction history (simulated)")
    public ResponseEntity<ApiResponse<JsonNode>> getTransactionHistory(@PathVariable String txId) {
        return blockchainRecordRepository.findByTransactionId(txId)
                .map(record -> {
                    var history = objectMapper.createArrayNode();
                    history.add(parseJson(record.getPayload()));
                    return ResponseEntity.ok(ApiResponse.success((JsonNode) history));
                })
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.error("Transaction not found: " + txId)));
    }

    @GetMapping("/transactions")
    @Operation(summary = "List blockchain records for a tenant (simulated)")
    public ResponseEntity<ApiResponse<Page<BlockchainRecord>>> listTransactions(
            @RequestParam String tenantId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<BlockchainRecord> records =
                blockchainRecordRepository.findByTenantIdOrderByCreatedAtDesc(tenantId, pageable);
        return ResponseEntity.ok(ApiResponse.success(records));
    }

    @PostMapping("/audit")
    @Operation(summary = "Anchor audit record (simulated)",
               description = "Persists a local hashed audit record and returns a simulated blockchain identifier.")
    public ResponseEntity<ApiResponse<BlockchainAuditResponse>> submitAuditEntry(
            @Valid @RequestBody BlockchainAuditRequest request) {

        log.info("[SIMULATED] Audit anchor: auditId={} tenant={} action={}",
                request.auditId(), request.tenantId(), request.action());

        String previousHash = simulatedAuditRecordRepository.findTopByTenantIdOrderByCreatedAtDesc(request.tenantId())
                .map(SimulatedAuditRecord::getRecordHash)
                .orElse(null);

        String blockchainTxId = simTxId();
        String blockNumber = nextSimBlockNumber();
        LocalDateTime occurredAt = request.occurredAt() != null ? request.occurredAt() : LocalDateTime.now();

        Map<String, Object> sanitisedPayload = new LinkedHashMap<>();
        sanitisedPayload.put("auditId", request.auditId());
        sanitisedPayload.put("entityType", request.entityType());
        sanitisedPayload.put("entityId", request.entityId());
        sanitisedPayload.put("action", request.action());
        sanitisedPayload.put("actorId", request.actorId());
        sanitisedPayload.put("occurredAt", occurredAt.toString());
        sanitisedPayload.put("sourcePayloadHash", hashJson(toJsonNode(request.payload())));

        String payloadJson = canonicalJson(sanitisedPayload);
        String payloadHash = sha256Hex(payloadJson);
        String recordHash = sha256Hex(String.join("|",
                "AUDIT",
                defaultString(request.auditId(), ""),
                defaultString(request.tenantId(), ""),
                defaultString(request.entityType(), ""),
                defaultString(request.entityId(), ""),
                defaultString(request.action(), ""),
                defaultString(request.actorId(), ""),
                payloadHash,
                defaultString(previousHash, "")
        ));

        Map<String, Object> recordMap = new LinkedHashMap<>();
        recordMap.put("auditId", request.auditId());
        recordMap.put("tenantId", request.tenantId());
        recordMap.put("entityType", request.entityType());
        recordMap.put("entityId", request.entityId());
        recordMap.put("action", request.action());
        recordMap.put("actorId", request.actorId());
        recordMap.put("actorType", "SYSTEM");
        recordMap.put("payload", payloadJson);
        recordMap.put("payloadHash", payloadHash);
        recordMap.put("previousHash", previousHash);
        recordMap.put("recordHash", recordHash);
        recordMap.put("occurredAt", occurredAt.toString());
        recordMap.put("blockchainTxId", blockchainTxId);
        recordMap.put("docType", "AUDIT");
        recordMap.put("schemaVersion", AUDIT_SCHEMA_VERSION);

        SimulatedAuditRecord record = simulatedAuditRecordRepository.findByAuditId(request.auditId())
                .orElseGet(() -> SimulatedAuditRecord.builder()
                        .auditId(request.auditId())
                        .tenantId(request.tenantId())
                        .build());

        record.setTenantId(request.tenantId());
        record.setBlockchainTxId(blockchainTxId);
        record.setBlockNumber(blockNumber);
        record.setPayloadHash(payloadHash);
        record.setRecordHash(recordHash);
        record.setPreviousHash(previousHash);
        record.setVerificationStatus(VERIFICATION_VERIFIED);
        record.setRecordJson(canonicalJson(recordMap));
        simulatedAuditRecordRepository.save(record);

        BlockchainAuditResponse response = new BlockchainAuditResponse(
                request.auditId(),
                blockchainTxId,
                blockNumber,
                VERIFICATION_VERIFIED,
                true);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Audit record anchored (simulated)", response));
    }

    @GetMapping("/audit/{auditId}")
    @Operation(summary = "Get audit record (simulated)")
    public ResponseEntity<ApiResponse<JsonNode>> getAuditRecord(@PathVariable String auditId) {
        return simulatedAuditRecordRepository.findByAuditId(auditId)
                .map(record -> ResponseEntity.ok(ApiResponse.success(parseJson(record.getRecordJson()))))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.error("Audit record not found: " + auditId)));
    }

    @GetMapping("/audit")
    @Operation(summary = "List audit records (simulated)")
    public ResponseEntity<ApiResponse<JsonNode>> listAuditRecords(
            @RequestParam String tenantId,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) String entityId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {

        var records = objectMapper.createArrayNode();
        LocalDateTime rangeStart = fromDate != null ? fromDate.atStartOfDay() : null;
        LocalDateTime rangeEnd = toDate != null ? toDate.atTime(LocalTime.MAX) : null;

        simulatedAuditRecordRepository.findAll().stream()
                .filter(record -> tenantId.equals(record.getTenantId()))
                .map(record -> parseJson(record.getRecordJson()))
                .filter(node -> matchesAuditFilters(node, entityType, entityId, action, rangeStart, rangeEnd))
                .forEach(records::add);

        return ResponseEntity.ok(ApiResponse.success((JsonNode) records));
    }

    @GetMapping("/audit/{auditId}/verify")
    @Operation(summary = "Verify audit integrity (simulated)")
    public ResponseEntity<ApiResponse<BlockchainVerificationResponse>> verifyAuditRecord(
            @PathVariable String auditId) {

        return simulatedAuditRecordRepository.findByAuditId(auditId)
                .map(record -> {
                    BlockchainVerificationResponse response = buildAuditVerification(record);
                    blockchainEventPublisher.publishVerificationUpdated(
                            "AUDIT",
                            auditId,
                            record.getTenantId(),
                            response.verificationStatus(),
                            response.valid(),
                            response.payloadHash(),
                            response.recordHash(),
                            response.previousHash(),
                            response.verifiedAt()
                    );
                    return ResponseEntity.ok(ApiResponse.success(response));
                })
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.error("Audit record not found: " + auditId)));
    }

    @GetMapping("/audit/{auditId}/history")
    @Operation(summary = "Get audit history (simulated)")
    public ResponseEntity<ApiResponse<JsonNode>> getAuditHistory(@PathVariable String auditId) {
        return simulatedAuditRecordRepository.findByAuditId(auditId)
                .map(record -> {
                    var history = objectMapper.createArrayNode();
                    history.add(parseJson(record.getRecordJson()));
                    return ResponseEntity.ok(ApiResponse.success((JsonNode) history));
                })
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.error("Audit record not found: " + auditId)));
    }

    @GetMapping("/health/fabric")
    @Operation(summary = "Fabric network health (simulated)")
    public ResponseEntity<ApiResponse<Map<String, Object>>> checkFabricHealth() {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("status", "SIMULATED");
        info.put("mode", "SIMULATED_FALLBACK");
        info.put("channel", "simulated");
        info.put("peerEndpoint", "none");
        info.put("effectiveHealth", "DEGRADED");
        info.put("message", "Hyperledger Fabric integration is disabled (fabric.enabled=false). "
                + "All blockchain operations are persisted locally.");
        return ResponseEntity.ok(ApiResponse.success("Fabric is disabled \u2014 running in simulation mode", info));
    }

    @GetMapping("/health/service")
    @Operation(summary = "Blockchain service health (simulated)",
               description = "Returns overall blockchain service health. Use /health/fabric for Fabric-specific status.")
    public ResponseEntity<ApiResponse<Map<String, Object>>> checkHealth() {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("status", "UP");
        info.put("mode", "SIMULATED_FALLBACK");
        info.put("fabricEnabled", false);
        info.put("message", "Blockchain service is running in simulation mode (fabric.enabled=false).");
        return ResponseEntity.ok(ApiResponse.success("Blockchain service is healthy", info));
    }

    private BlockchainVerificationResponse buildTransactionVerification(BlockchainRecord record) {
        String canonicalPayload = canonicalJson(parseJson(record.getPayload()));
        String recomputedPayloadHash = sha256Hex(canonicalPayload);
        String recomputedRecordHash = sha256Hex(String.join("|",
                "TRANSACTION",
                defaultString(record.getTransactionId(), ""),
                defaultString(record.getTenantId(), ""),
                defaultString(record.getStatus(), "UNKNOWN"),
                recomputedPayloadHash,
                defaultString(record.getPreviousHash(), "")
        ));
        boolean valid = recomputedPayloadHash.equals(record.getPayloadHash())
                && recomputedRecordHash.equals(record.getRecordHash());

        record.setVerificationStatus(valid ? VERIFICATION_VERIFIED : VERIFICATION_HASH_MISMATCH);
        blockchainRecordRepository.save(record);

        return new BlockchainVerificationResponse(
                record.getTransactionId(),
                record.getVerificationStatus(),
                valid,
                record.getPayloadHash(),
                recomputedPayloadHash,
                record.getRecordHash(),
                recomputedRecordHash,
                record.getPreviousHash(),
                LocalDateTime.now().toString()
        );
    }

    private BlockchainVerificationResponse buildAuditVerification(SimulatedAuditRecord record) {
        JsonNode recordNode = parseJson(record.getRecordJson());
        String payloadJson = canonicalJson(toJsonNode(recordNode.path("payload").asText("{}")));
        String recomputedPayloadHash = sha256Hex(payloadJson);
        String recomputedRecordHash = sha256Hex(String.join("|",
                "AUDIT",
                recordNode.path("auditId").asText(""),
                recordNode.path("tenantId").asText(""),
                recordNode.path("entityType").asText(""),
                recordNode.path("entityId").asText(""),
                recordNode.path("action").asText(""),
                recordNode.path("actorId").asText(""),
                recomputedPayloadHash,
                recordNode.path("previousHash").asText("")
        ));
        boolean valid = recomputedPayloadHash.equals(record.getPayloadHash())
                && recomputedRecordHash.equals(record.getRecordHash());

        record.setVerificationStatus(valid ? VERIFICATION_VERIFIED : VERIFICATION_HASH_MISMATCH);
        simulatedAuditRecordRepository.save(record);

        return new BlockchainVerificationResponse(
                record.getAuditId(),
                record.getVerificationStatus(),
                valid,
                record.getPayloadHash(),
                recomputedPayloadHash,
                record.getRecordHash(),
                recomputedRecordHash,
                record.getPreviousHash(),
                LocalDateTime.now().toString()
        );
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
        payload.put("decision", request.decision());
        payload.put("decisionReason", request.decisionReason());
        payload.put("timestamp", request.timestamp() != null ? request.timestamp().toString() : null);
        payload.put("ipAddressHash", hashValue(request.ipAddress()));
        if (request.metadata() != null && !request.metadata().isEmpty()) {
            payload.put("metadataHash", hashJson(request.metadata()));
        }
        return payload;
    }

    private JsonNode parseJson(String json) {
        try {
            return objectMapper.readTree(defaultString(json, "{}"));
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to parse simulated blockchain JSON", ex);
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
            } catch (Exception ignored) {
                Map<String, Object> wrapper = new LinkedHashMap<>();
                wrapper.put("raw", raw);
                return objectMapper.valueToTree(wrapper);
            }
        }
        return objectMapper.valueToTree(value);
    }

    private String canonicalJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to canonicalise simulated blockchain JSON", ex);
        }
    }

    private String hashJson(Object value) {
        return sha256Hex(canonicalJson(value));
    }

    private String hashValue(String value) {
        return value == null || value.isBlank() ? null : sha256Hex(value.trim());
    }

    private String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(defaultString(input, "").getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to calculate SHA-256 hash", ex);
        }
    }

    private String defaultString(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String maskAccount(String account) {
        if (account == null || account.isBlank()) {
            return null;
        }
        String trimmed = account.trim();
        // Extract only digits for standard masking
        String digits = trimmed.replaceAll("\\D", "");

        // Industry standard: 10–12 digit accounts — show first 4, mask middle, show last 4
        if (digits.length() >= 10 && digits.length() <= 12) {
            return digits.substring(0, 4) + " **** " + digits.substring(digits.length() - 4);
        }

        // 8–9 digit legacy accounts — partial mask: first 3, ***, last 3
        if (digits.length() >= 8) {
            return digits.substring(0, 3) + " *** " + digits.substring(digits.length() - 3);
        }

        // Short or alpha-numeric legacy format — store as-is (clearly non-standard)
        return trimmed + " (legacy)";
    }

    private boolean matchesAuditFilters(
            JsonNode node,
            String entityType,
            String entityId,
            String action,
            LocalDateTime fromDate,
            LocalDateTime toDate) {
        if (entityType != null && !entityType.isBlank()
                && !entityType.equalsIgnoreCase(node.path("entityType").asText())) {
            return false;
        }
        if (entityId != null && !entityId.isBlank()
                && !entityId.equals(node.path("entityId").asText())) {
            return false;
        }
        if (action != null && !action.isBlank()
                && !action.equalsIgnoreCase(node.path("action").asText())) {
            return false;
        }

        if (fromDate == null && toDate == null) {
            return true;
        }

        LocalDateTime occurredAt;
        try {
            occurredAt = LocalDateTime.parse(node.path("occurredAt").asText());
        } catch (Exception ex) {
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
