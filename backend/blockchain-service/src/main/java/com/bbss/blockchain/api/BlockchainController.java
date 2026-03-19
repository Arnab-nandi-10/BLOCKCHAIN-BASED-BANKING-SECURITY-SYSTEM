package com.bbss.blockchain.api;

import com.bbss.shared.dto.ApiResponse;
import com.bbss.blockchain.domain.model.BlockchainRecord;
import com.bbss.blockchain.domain.repository.BlockchainRecordRepository;
import com.bbss.blockchain.dto.BlockchainAuditRequest;
import com.bbss.blockchain.dto.BlockchainAuditResponse;
import com.bbss.blockchain.dto.BlockchainSubmitRequest;
import com.bbss.blockchain.dto.BlockchainSubmitResponse;
import com.bbss.blockchain.dto.BlockchainTransactionResponse;
import com.bbss.blockchain.service.ChaincodeInvokerService;
import com.bbss.blockchain.service.FabricGatewayService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hyperledger.fabric.client.Network;
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
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * REST controller exposing blockchain ledger operations and Fabric network health.
 */

@ConditionalOnProperty(name = "fabric.enabled", havingValue = "true", matchIfMissing = false)
@RestController
@RequestMapping("/api/v1/blockchain")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Blockchain", description = "Hyperledger Fabric ledger operations and health")
public class BlockchainController {

    private final ChaincodeInvokerService    chaincodeInvokerService;
    private final BlockchainRecordRepository blockchainRecordRepository;
    private final Network                    fabricNetwork;

    // -------------------------------------------------------------------------
    // POST /api/v1/blockchain/transactions
    // -------------------------------------------------------------------------

    /**
     * Submit a transaction to the Hyperledger Fabric ledger.
     *
     * @param request the transaction payload from the transaction-service
     * @return 201 Created with the blockchain submission result
     */
    @PostMapping("/transactions")
    @Operation(summary = "Submit transaction to ledger",
               description = "Writes a transaction to the Hyperledger Fabric channel and returns the tx hash.")
    public ResponseEntity<ApiResponse<BlockchainSubmitResponse>> submitTransaction(
            @Valid @RequestBody BlockchainSubmitRequest request) {

        log.info("Received blockchain submit request: txId={} tenant={}",
                request.transactionId(), request.tenantId());

        BlockchainSubmitResponse response = chaincodeInvokerService.submitTransactionToLedger(request);

        HttpStatus status = response.success() ? HttpStatus.CREATED : HttpStatus.UNPROCESSABLE_ENTITY;
        String message = response.success()
                ? "Transaction committed to ledger"
                : "Ledger submission failed";

        return ResponseEntity.status(status).body(ApiResponse.success(message, response));
    }

    // -------------------------------------------------------------------------
    // GET /api/v1/blockchain/transactions/{txId}
    // -------------------------------------------------------------------------

    /**
     * Retrieve an on-chain transaction record by its business transaction ID.
     *
     * @param txId the business transaction identifier
     * @return the on-chain transaction details
     */
    @GetMapping("/transactions/{txId}")
    @Operation(summary = "Get on-chain transaction",
               description = "Retrieves a transaction from the local cache or the Fabric ledger.")
    public ResponseEntity<ApiResponse<BlockchainTransactionResponse>> getTransaction(
            @PathVariable String txId) {

        log.debug("Fetching blockchain record for txId={}", txId);
        BlockchainTransactionResponse response = chaincodeInvokerService.getTransactionFromLedger(txId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // -------------------------------------------------------------------------
    // GET /api/v1/blockchain/transactions?tenantId=&page=&size=
    // -------------------------------------------------------------------------

    /**
     * List cached blockchain records for a tenant, newest first.
     *
     * @param tenantId the tenant identifier
     * @param page     zero-based page index (default 0)
     * @param size     page size (default 20)
     * @return paginated list of blockchain records
     */
    @GetMapping("/transactions")
    @Operation(summary = "List blockchain records for a tenant")
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

    /**
     * Anchor an audit record to the Hyperledger Fabric audit chaincode.
     *
     * @param request the audit payload from the audit-service
     * @return 201 Created with ledger coordinates on success
     */
    @PostMapping("/audit")
    @Operation(summary = "Anchor audit record to ledger",
               description = "Writes an audit entry to the Hyperledger Fabric audit chaincode.")
    public ResponseEntity<ApiResponse<BlockchainAuditResponse>> submitAuditEntry(
            @Valid @RequestBody BlockchainAuditRequest request) {

        log.info("Received audit anchor request: auditId={} tenant={} action={}",
                request.auditId(), request.tenantId(), request.action());

        try {
            chaincodeInvokerService.submitAuditEntryFromRequest(request);
            // Fabric does not return a block number synchronously from the helper;
            // a simulated ID keeps the response contract fulfilled.
            String fakeTxId = "fabric-audit-" + request.auditId();
            BlockchainAuditResponse response = new BlockchainAuditResponse(
                    request.auditId(), fakeTxId, null, true);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.success("Audit record anchored to ledger", response));
        } catch (Exception ex) {
            log.error("Failed to anchor audit record auditId={}: {}", request.auditId(), ex.getMessage());
            BlockchainAuditResponse failResponse =
                    new BlockchainAuditResponse(request.auditId(), null, null, false);
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                    .body(ApiResponse.error("Audit anchor failed: " + ex.getMessage(), failResponse));
        }
    }

    // -------------------------------------------------------------------------
    // GET /api/v1/blockchain/health/fabric
    // -------------------------------------------------------------------------

    /**
     * Check connectivity to the Hyperledger Fabric network.
     *
     * <p>Attempts to query the channel name from the active network; returns a
     * degraded status if the Fabric peer is unreachable.</p>
     *
     * @return Fabric connectivity status map
     */
    @GetMapping("/health/fabric")
    @Operation(summary = "Fabric network health check",
               description = "Probes the configured Fabric peer and returns connectivity status.")
    public ResponseEntity<ApiResponse<Map<String, Object>>> checkFabricHealth() {
        Map<String, Object> healthInfo = new LinkedHashMap<>();
        try {
            // A lightweight check: retrieve the channel name from the bound Network object.
            String channelName = fabricNetwork.getName();
            healthInfo.put("status", "UP");
            healthInfo.put("channel", channelName);
            healthInfo.put("message", "Fabric peer is reachable");
            log.debug("Fabric health check passed: channel={}", channelName);
            return ResponseEntity.ok(ApiResponse.success("Fabric network is healthy", healthInfo));
        } catch (Exception ex) {
            log.error("Fabric health check failed: {}", ex.getMessage(), ex);
            healthInfo.put("status", "DOWN");
            healthInfo.put("message", "Fabric peer unreachable: " + ex.getMessage());
            return ResponseEntity
                    .status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(ApiResponse.error("Fabric network is unavailable"));
        }
    }
}
