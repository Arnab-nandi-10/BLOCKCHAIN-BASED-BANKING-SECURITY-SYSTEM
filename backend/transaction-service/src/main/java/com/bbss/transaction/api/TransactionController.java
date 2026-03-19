package com.bbss.transaction.api;

import com.bbss.shared.dto.ApiResponse;
import com.bbss.transaction.domain.model.TransactionStatus;
import com.bbss.transaction.dto.SubmitTransactionRequest;
import com.bbss.transaction.dto.TransactionResponse;
import com.bbss.transaction.dto.TransactionStatsResponse;
import com.bbss.transaction.service.TransactionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller exposing transaction management endpoints.
 *
 * <p>All endpoints require the {@code X-Tenant-ID} request header.
 * The header value is used as the tenant scope for every operation.</p>
 */
@RestController
@RequestMapping("/api/v1/transactions")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Transactions", description = "Transaction submission and lifecycle management")
public class TransactionController {

    private final TransactionService transactionService;

    // -------------------------------------------------------------------------
    // POST /api/v1/transactions
    // -------------------------------------------------------------------------

    /**
     * Submit a new transaction for fraud evaluation and blockchain recording.
     *
     * <p>Requires ADMIN or ANALYST role.</p>
     *
     * @param tenantId the tenant scoping this request
     * @param request  validated transaction payload
     * @param http     servlet request used to extract the submitter IP address
     * @return 201 Created with the created transaction response
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'ANALYST')")
    @Operation(summary = "Submit a new transaction",
               description = "Runs fraud evaluation and blockchain submission. Requires ADMIN or ANALYST role.")
    public ResponseEntity<ApiResponse<TransactionResponse>> submitTransaction(
            @RequestHeader("X-Tenant-ID")
            @Parameter(description = "Tenant identifier", required = true)
            String tenantId,
            @Valid @RequestBody SubmitTransactionRequest request,
            HttpServletRequest http) {

        String ipAddress = resolveClientIp(http);
        log.info("Submitting transaction for tenant={} from ip={}", tenantId, ipAddress);

        TransactionResponse response = transactionService.submitTransaction(request, tenantId, ipAddress);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("Transaction submitted successfully", response));
    }

    // -------------------------------------------------------------------------
    // GET /api/v1/transactions
    // -------------------------------------------------------------------------

    /**
     * List all transactions for the caller's tenant, newest first.
     *
     * @param tenantId the tenant scoping this request
     * @param page     zero-based page index (default 0)
     * @param size     page size (default 20)
     * @return paginated list of transaction responses
     */
    @GetMapping
    @Operation(summary = "List transactions", description = "Returns paginated transactions for the tenant.")
    public ResponseEntity<ApiResponse<Page<TransactionResponse>>> listTransactions(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<TransactionResponse> result = transactionService.listTransactions(tenantId, pageable);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    // -------------------------------------------------------------------------
    // GET /api/v1/transactions/stats
    // -------------------------------------------------------------------------

    /**
     * Return aggregate transaction statistics for the past 24 hours.
     *
     * @param tenantId the tenant scoping this request
     * @return statistics covering submission, verification, fraud and failure counts
     */
    @GetMapping("/stats")
    @Operation(summary = "Get transaction statistics", description = "24-hour rolling statistics for the tenant.")
    public ResponseEntity<ApiResponse<TransactionStatsResponse>> getTransactionStats(
            @RequestHeader("X-Tenant-ID") String tenantId) {

        TransactionStatsResponse stats = transactionService.getTransactionStats(tenantId);
        return ResponseEntity.ok(ApiResponse.success(stats));
    }

    // -------------------------------------------------------------------------
    // GET /api/v1/transactions/status/{status}
    // -------------------------------------------------------------------------

    /**
     * Filter transactions by lifecycle status.
     *
     * @param tenantId the tenant scoping this request
     * @param status   transaction status to filter by
     * @param page     zero-based page index (default 0)
     * @param size     page size (default 20)
     * @return paginated list of matching transaction responses
     */
    @GetMapping("/status/{status}")
    @Operation(summary = "List transactions by status")
    public ResponseEntity<ApiResponse<Page<TransactionResponse>>> listByStatus(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @PathVariable TransactionStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<TransactionResponse> result = transactionService.listByStatus(tenantId, status, pageable);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    // -------------------------------------------------------------------------
    // GET /api/v1/transactions/{txId}
    // -------------------------------------------------------------------------

    /**
     * Retrieve a single transaction by its business transaction ID.
     *
     * @param tenantId the tenant scoping this request
     * @param txId     business transaction identifier
     * @return the transaction response
     */
    @GetMapping("/{txId}")
    @Operation(summary = "Get transaction by ID")
    public ResponseEntity<ApiResponse<TransactionResponse>> getTransaction(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @PathVariable String txId) {

        TransactionResponse response = transactionService.getTransaction(txId, tenantId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // -------------------------------------------------------------------------
    // Helper
    // -------------------------------------------------------------------------

    /**
     * Resolve the real client IP, honouring common reverse-proxy headers.
     */
    private String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            // X-Forwarded-For may contain a comma-separated chain; the first is the originator.
            return forwarded.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }
}
