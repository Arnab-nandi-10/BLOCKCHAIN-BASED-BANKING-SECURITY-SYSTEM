package com.bbss.tenant.service;

import com.bbss.shared.events.TenantEvent;
import com.bbss.shared.exception.BusinessException;
import com.bbss.shared.exception.TenantNotFoundException;
import com.bbss.tenant.domain.model.Tenant;
import com.bbss.tenant.domain.model.TenantStatus;
import com.bbss.tenant.domain.repository.TenantRepository;
import com.bbss.tenant.dto.CreateTenantRequest;
import com.bbss.tenant.dto.TenantResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
@Transactional
public class TenantService {

    private static final Logger log = LoggerFactory.getLogger(TenantService.class);
    private static final String TENANT_TOPIC = "tenant.provisioned";
    private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^a-z0-9]");
    private static final Pattern CONSECUTIVE_HYPHENS = Pattern.compile("-+");
    private static final Pattern LEADING_TRAILING_HYPHENS = Pattern.compile("^-|-$");

    private final TenantRepository tenantRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public TenantService(TenantRepository tenantRepository,
                         KafkaTemplate<String, Object> kafkaTemplate) {
        this.tenantRepository = tenantRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    public TenantResponse createTenant(CreateTenantRequest req) {
        String tenantId = generateTenantSlug(req.name());

        if (tenantRepository.existsByTenantId(tenantId)) {
            throw new BusinessException("TENANT_ID_EXISTS",
                    "Tenant with ID '" + tenantId + "' already exists");
        }

        String apiKey = UUID.randomUUID().toString().replace("-", "");

        Tenant tenant = Tenant.builder()
                .tenantId(tenantId)
                .name(req.name())
                .adminEmail(req.adminEmail())
                .plan(req.plan())
                .status(TenantStatus.PENDING)
                .apiKey(apiKey)
                .webhookUrl(req.webhookUrl())
                .config(new HashMap<>())
                .build();

        Tenant saved = tenantRepository.save(tenant);
        publishEvent(saved, "PROVISIONED");

        log.info("Tenant created successfully: tenantId={}", tenantId);
        return toResponse(saved);
    }

    public TenantResponse activateTenant(String tenantId) {
        Tenant tenant = findTenantBySlug(tenantId);
        tenant.setStatus(TenantStatus.ACTIVE);
        Tenant saved = tenantRepository.save(tenant);
        publishEvent(saved, "ACTIVATED");
        log.info("Tenant activated: tenantId={}", tenantId);
        return toResponse(saved);
    }

    public TenantResponse suspendTenant(String tenantId) {
        Tenant tenant = findTenantBySlug(tenantId);
        tenant.setStatus(TenantStatus.SUSPENDED);
        Tenant saved = tenantRepository.save(tenant);
        publishEvent(saved, "SUSPENDED");
        log.info("Tenant suspended: tenantId={}", tenantId);
        return toResponse(saved);
    }

    public void deleteTenant(String tenantId) {
        Tenant tenant = findTenantBySlug(tenantId);
        tenant.setStatus(TenantStatus.DELETED);
        Tenant saved = tenantRepository.save(tenant);
        publishEvent(saved, "DELETED");
        log.info("Tenant soft-deleted: tenantId={}", tenantId);
    }

    @Transactional(readOnly = true)
    public TenantResponse getTenant(String tenantId) {
        Tenant tenant = findTenantBySlug(tenantId);
        return toResponse(tenant);
    }

    @Transactional(readOnly = true)
    public Page<TenantResponse> listTenants(Pageable pageable) {
        return tenantRepository.findAll(pageable).map(this::toResponse);
    }

    public TenantResponse updateTenantConfig(String tenantId, Map<String, String> config) {
        Tenant tenant = findTenantBySlug(tenantId);
        if (tenant.getConfig() == null) {
            tenant.setConfig(new HashMap<>());
        }
        tenant.getConfig().putAll(config);
        Tenant saved = tenantRepository.save(tenant);
        log.info("Tenant config updated: tenantId={}, keys={}", tenantId, config.keySet());
        return toResponse(saved);
    }

    public TenantResponse regenerateApiKey(String tenantId) {
        Tenant tenant = findTenantBySlug(tenantId);
        String newApiKey = UUID.randomUUID().toString().replace("-", "");
        tenant.setApiKey(newApiKey);
        Tenant saved = tenantRepository.save(tenant);
        log.info("API key regenerated for tenantId={}", tenantId);
        return toResponse(saved);
    }

    private Tenant findTenantBySlug(String tenantId) {
        return tenantRepository.findByTenantId(tenantId)
                .orElseThrow(() -> new TenantNotFoundException("Tenant not found with ID: " + tenantId));
    }

    private TenantResponse toResponse(Tenant tenant) {
        return TenantResponse.builder()
                .tenantId(tenant.getTenantId())
                .name(tenant.getName())
                .adminEmail(tenant.getAdminEmail())
                .status(tenant.getStatus())
                .plan(tenant.getPlan())
                .apiKey(tenant.getApiKey())
                .webhookUrl(tenant.getWebhookUrl())
                .createdAt(tenant.getCreatedAt())
                .updatedAt(tenant.getUpdatedAt())
                .config(tenant.getConfig())
                .monthlyTransactionLimit(tenant.getPlan().getMonthlyTransactionLimit())
                .maxUsers(tenant.getPlan().getMaxUsers())
                .build();
    }

    private void publishEvent(Tenant tenant, String action) {
        try {
            TenantEvent event = TenantEvent.builder()
                    .tenantId(tenant.getTenantId())
                    .action(action)
                    .tenantName(tenant.getName())
                    .adminEmail(tenant.getAdminEmail())
                    .planType(tenant.getPlan().name())
                    .timestamp(LocalDateTime.now())
                    .build();
            kafkaTemplate.send(TENANT_TOPIC, tenant.getTenantId(), event);
            log.debug("Published tenant event: action={}, tenantId={}", action, tenant.getTenantId());
        } catch (Exception e) {
            log.error("Failed to publish tenant event: tenantId={}, action={}, error={}",
                    tenant.getTenantId(), action, e.getMessage(), e);
        }
    }

    private String generateTenantSlug(String name) {
        String normalized = Normalizer.normalize(name.toLowerCase(), Normalizer.Form.NFD);
        String slug = NON_ALPHANUMERIC.matcher(normalized).replaceAll("-");
        slug = CONSECUTIVE_HYPHENS.matcher(slug).replaceAll("-");
        slug = LEADING_TRAILING_HYPHENS.matcher(slug).replaceAll("");
        return slug;
    }
}
