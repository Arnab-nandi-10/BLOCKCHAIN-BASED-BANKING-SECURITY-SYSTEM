package com.bbss.transaction.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.Map;

@FeignClient(name = "tenant-service", url = "${services.tenant-service.url}")
public interface TenantServiceClient {

    @GetMapping("/api/v1/tenants/{tenantId}")
    ApiResponse<TenantResponse> getTenant(@PathVariable("tenantId") String tenantId);

    record TenantResponse(
            String tenantId,
            Map<String, String> config
    ) {}

    record ApiResponse<T>(
            boolean success,
            String message,
            T data
    ) {}
}
