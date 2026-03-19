package com.bbss.tenant.dto;

import com.bbss.tenant.domain.model.SubscriptionPlan;
import com.bbss.tenant.domain.model.TenantStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.Map;

@Getter
@Builder
public class TenantResponse {

    private String tenantId;
    private String name;
    private String adminEmail;
    private TenantStatus status;
    private SubscriptionPlan plan;
    private String apiKey;
    private String webhookUrl;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Map<String, String> config;
    private int monthlyTransactionLimit;
    private int maxUsers;
}
