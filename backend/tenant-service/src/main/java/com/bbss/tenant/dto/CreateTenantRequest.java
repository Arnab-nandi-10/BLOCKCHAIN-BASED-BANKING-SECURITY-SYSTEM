package com.bbss.tenant.dto;

import com.bbss.tenant.domain.model.SubscriptionPlan;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateTenantRequest(
        @NotBlank(message = "Tenant name is required")
        @Size(min = 2, max = 100, message = "Tenant name must be between 2 and 100 characters")
        String name,

        @NotBlank(message = "Admin email is required")
        @Email(message = "Admin email must be a valid email address")
        String adminEmail,

        @NotNull(message = "Subscription plan is required")
        SubscriptionPlan plan,

        String webhookUrl
) {}
