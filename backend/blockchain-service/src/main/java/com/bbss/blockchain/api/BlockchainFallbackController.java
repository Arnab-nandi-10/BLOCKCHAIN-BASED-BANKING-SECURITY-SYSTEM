package com.bbss.blockchain.api;

import com.bbss.shared.dto.ApiResponse;
import com.bbss.blockchain.domain.model.BlockchainRecord;
import com.bbss.blockchain.domain.repository.BlockchainRecordRepository;
import com.bbss.blockchain.dto.BlockchainAuditRequest;
import com.bbss.blockchain.dto.BlockchainAuditResponse;
import com.bbss.blockchain.dto.BlockchainSubmitRequest;
import com.bbss.blockchain.dto.BlockchainSubmitResponse;
import com.bbss.blockchain.dto.BlockchainTransactionResponse;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Fallback REST controller that is active when {@code fabric.enabled=false}.
 *
 * <p>When Hyperledger Fabric is not available (the default for local and CI
 * deployments), this controller exposes the same API contract as
 * {@link BlockchainController} but persists records to the local PostgreSQL
 * cache and returns simulated blockchain identifiers.  This prevents the
 * transaction-service and audit-service circuit breakers from opening due to
 * HTTP 404 responses.
 *
 * <p>All simulated blockchain TX IDs have the prefix {@code sim-} so they are
 * easily distinguishable from real Hyperledger Fabric hashes.
 */
@ConditionalOnProperty(name = "fabric.enabled", havingValue = "false", matchIfMissing = true)
@RestController
@RequestMapping("/api/v1/blockchain")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Blockchain (Simulated)", description = "Simulated blockchain endpoints — Fabric is disabled")
public class BlockchainFallbackController {

    private final BlockchainRecordRepository blockchainRecordRepository;

    // ── Simulated block counter prefix ────────────────────────────────────────

    private static long simulatedBlockCounter = 1_000_000L;

    private static synchronized String nextSimBlockNumber() {
        return String.valueOf(simulatedBlockCounter++);
    }

    private static String simTxId() {
        return "sim-" + UUID.randomUUID().toString().replace("-", "");
    }

    // -------------------------------------------------------------------------
    // POST /api/v1/blockchain/transactions
    // -------------------------------------------------------------------------

    @PostMapping("/transactions")
    @Operation(summary = "Submit transaction to ledger (simulated)",
               description = "Persists a local record and returns a simulated blockchain TX ID.")
    public ResponseEntity<ApiResponse<BlockchainSubmitResponse>> submitTransaction(
            @Valid @RequestBody BlockchainSubmitRequest request) {

        log.info("[SIMULATED] Blockchain submit: txId={} tenant={}",
                request.transactionId(), request.tenantId());

        String blockchainTxId = simTxId();
        String blockNumber    = nextSimBlockNumber();

        // Persist to local cache so GET requests can resolve without 404.
        BlockchainRecord record = BlockchainRecord.builder()
                .transactionId(request.transactionId())
                .tenantId(request.tenantId())
                .blockchainTxId(blockchainTxId)
                .blockNumber(blockNumber)
                .chaincodeId("transaction-cc-simulated")
                .status(request.status())
                .build();
        blockchainRecordRepository.save(record);

        BlockchainSubmitResponse response = new BlockchainSubmitResponse(
                request.transactionId(),
                blockchainTxId,
                blockNumber,
                true,
                "Simulated ledger write — Fabric is disabled");

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Transaction recorded (simulated)", response));
    }

    // -------------------------------------------------------------------------
    // GET /api/v1/blockchain/transactions/{txId}
    // -------------------------------------------------------------------------

    @GetMapping("/transactions/{txId}")
    @Operation(summary = "Get transaction record (simulated)")
    public ResponseEntity<ApiResponse<BlockchainTransactionResponse>> getTransaction(
            @PathVariable String txId) {

        return blockchainRecordRepository.findByTransactionId(txId)
                .map(rec -> {
                    BlockchainTransactionResponse r = new BlockchainTransactionResponse(
                            rec.getTransactionId(),
                            rec.getBlockchainTxId(),
                            rec.getBlockNumber(),
                            rec.getStatus(),
                            rec.getCreatedAt());
                    return ResponseEntity.ok(ApiResponse.success(r));
                })
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.error("Transaction not found: " + txId)));
    }

    // -------------------------------------------------------------------------
    // GET /api/v1/blockchain/transactions?tenantId=&page=&size=
    // -------------------------------------------------------------------------

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

    // -------------------------------------------------------------------------
    // POST /api/v1/blockchain/audit
    // -------------------------------------------------------------------------

    @PostMapping("/audit")
    @Operation(summary = "Anchor audit record (simulated)",
               description = "Returns a simulated audit blockchain identifier. Fabric is disabled.")
    public ResponseEntity<ApiResponse<BlockchainAuditResponse>> submitAuditEntry(
            @Valid @RequestBody BlockchainAuditRequest request) {

        log.info("[SIMULATED] Audit anchor: auditId={} tenant={} action={}",
                request.auditId(), request.tenantId(), request.action());

        String blockchainTxId = simTxId();
        BlockchainAuditResponse response = new BlockchainAuditResponse(
                request.auditId(), blockchainTxId, nextSimBlockNumber(), true);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Audit record anchored (simulated)", response));
    }

    // -------------------------------------------------------------------------
    // GET /api/v1/blockchain/health/fabric
    // -------------------------------------------------------------------------

    @GetMapping("/health/fabric")
    @Operation(summary = "Fabric network health (simulated)")
    public ResponseEntity<ApiResponse<Map<String, Object>>> checkFabricHealth() {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("status", "SIMULATED");
        info.put("mode", "fabric-disabled");
        info.put("message", "Hyperledger Fabric integration is disabled (fabric.enabled=false). "
                + "All blockchain operations are persisted locally.");
        return ResponseEntity.ok(ApiResponse.success("Fabric is disabled — running in simulation mode", info));
    }
}
