package com.bbss.tenant.api;

import com.bbss.shared.dto.ApiResponse;
import com.bbss.tenant.dto.CreateTenantRequest;
import com.bbss.tenant.dto.TenantResponse;
import com.bbss.tenant.service.TenantService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/tenants")
@Tag(name = "Tenant Management", description = "APIs for managing tenants in the BBSS platform")
public class TenantController {

    private final TenantService tenantService;

    public TenantController(TenantService tenantService) {
        this.tenantService = tenantService;
    }

    @PostMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Create a new tenant", description = "Creates a new tenant with PENDING status")
    public ResponseEntity<ApiResponse<TenantResponse>> createTenant(
            @Valid @RequestBody CreateTenantRequest request) {
        TenantResponse response = tenantService.createTenant(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("Tenant created successfully", response));
    }

    @GetMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "List all tenants", description = "Returns a paginated list of all tenants")
    public ResponseEntity<ApiResponse<Page<TenantResponse>>> listTenants(
            @PageableDefault(size = 20) Pageable pageable) {
        Page<TenantResponse> page = tenantService.listTenants(pageable);
        return ResponseEntity.ok(ApiResponse.success(page));
    }

    @GetMapping("/{tenantId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    @Operation(summary = "Get tenant by ID", description = "Returns tenant details for the given tenantId slug")
    public ResponseEntity<ApiResponse<TenantResponse>> getTenant(
            @PathVariable String tenantId) {
        TenantResponse response = tenantService.getTenant(tenantId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PutMapping("/{tenantId}/activate")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Activate tenant", description = "Changes tenant status to ACTIVE")
    public ResponseEntity<ApiResponse<TenantResponse>> activateTenant(
            @PathVariable String tenantId) {
        TenantResponse response = tenantService.activateTenant(tenantId);
        return ResponseEntity.ok(ApiResponse.success("Tenant activated successfully", response));
    }

    @PutMapping("/{tenantId}/suspend")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Suspend tenant", description = "Changes tenant status to SUSPENDED")
    public ResponseEntity<ApiResponse<TenantResponse>> suspendTenant(
            @PathVariable String tenantId) {
        TenantResponse response = tenantService.suspendTenant(tenantId);
        return ResponseEntity.ok(ApiResponse.success("Tenant suspended successfully", response));
    }

    @DeleteMapping("/{tenantId}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Delete tenant", description = "Soft-deletes the tenant by setting status to DELETED")
    public ResponseEntity<Void> deleteTenant(@PathVariable String tenantId) {
        tenantService.deleteTenant(tenantId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{tenantId}/config")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    @Operation(summary = "Update tenant configuration", description = "Merges provided key-value pairs into tenant config")
    public ResponseEntity<ApiResponse<TenantResponse>> updateTenantConfig(
            @PathVariable String tenantId,
            @RequestBody Map<String, String> config) {
        TenantResponse response = tenantService.updateTenantConfig(tenantId, config);
        return ResponseEntity.ok(ApiResponse.success("Tenant configuration updated successfully", response));
    }

    @PostMapping("/{tenantId}/api-key/regenerate")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Regenerate API key", description = "Generates a new API key for the tenant")
    public ResponseEntity<ApiResponse<TenantResponse>> regenerateApiKey(
            @PathVariable String tenantId) {
        TenantResponse response = tenantService.regenerateApiKey(tenantId);
        return ResponseEntity.ok(ApiResponse.success("API key regenerated successfully", response));
    }
}
