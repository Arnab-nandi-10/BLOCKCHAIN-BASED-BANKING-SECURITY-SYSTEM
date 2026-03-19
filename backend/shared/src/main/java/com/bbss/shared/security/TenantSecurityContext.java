package com.bbss.shared.security;

import com.bbss.shared.dto.TenantContext;
import com.bbss.shared.exception.BusinessException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Spring-managed component that provides security-aware access to the current
 * tenant context.
 *
 * <p>This class bridges the thread-local {@link TenantContext} with Spring
 * Security's {@link Authentication} model, offering three main capabilities:
 *
 * <ol>
 *   <li>Asserting that a tenant context is present ({@link #requireTenantId()})</li>
 *   <li>Safely reading the current tenant without throwing ({@link #getCurrentTenantId()})</li>
 *   <li>Checking whether the authenticated principal holds the super-admin role
 *       ({@link #isSuperAdmin(Authentication)})</li>
 * </ol>
 *
 * <p>Typical injection and usage in a service:
 * <pre>{@code
 * @Service
 * @RequiredArgsConstructor
 * public class TransactionService {
 *
 *     private final TenantSecurityContext tenantSecurityContext;
 *
 *     public TransactionDto createTransaction(CreateTransactionRequest req) {
 *         String tenantId = tenantSecurityContext.requireTenantId();
 *         // tenantId is guaranteed non-null here
 *         ...
 *     }
 * }
 * }</pre>
 */
@Component
public class TenantSecurityContext {

    /**
     * Spring Security authority string for the platform super-admin role.
     * A principal bearing this authority bypasses tenant isolation and can
     * access resources across all tenants.
     */
    private static final String ROLE_SUPER_ADMIN = "ROLE_SUPER_ADMIN";

    /**
     * Error code used in the {@link BusinessException} thrown when a required
     * tenant context is absent.
     */
    private static final String ERROR_CODE_MISSING_TENANT = "MISSING_TENANT_CONTEXT";

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Returns the tenant identifier bound to the current thread, guaranteeing
     * that it is non-null.
     *
     * <p>Call this method in service methods that absolutely require a tenant
     * context (i.e. the vast majority of transactional operations).  If the
     * context is absent – which indicates a configuration error in the security
     * filter chain or an unauthenticated code path – a {@link BusinessException}
     * is thrown so the global exception handler converts it into HTTP 400.
     *
     * @return the current tenant identifier; never {@code null}
     * @throws BusinessException with error code {@code "MISSING_TENANT_CONTEXT"}
     *                           if no tenant has been set on the current thread
     */
    public String requireTenantId() {
        String tenantId = TenantContext.getTenantId();
        if (tenantId == null || tenantId.isBlank()) {
            throw new BusinessException(
                    "No tenant context is associated with the current request. "
                            + "Ensure the tenant filter is correctly configured.",
                    ERROR_CODE_MISSING_TENANT);
        }
        return tenantId;
    }

    /**
     * Returns the tenant identifier bound to the current thread wrapped in an
     * {@link Optional}.
     *
     * <p>Use this method in code paths where the absence of a tenant context is
     * a valid scenario (e.g. health-check endpoints, system-level background
     * jobs, or super-admin operations that act across all tenants).
     *
     * @return an {@link Optional} containing the current tenant id, or
     *         {@link Optional#empty()} if none has been set
     */
    public Optional<String> getCurrentTenantId() {
        String tenantId = TenantContext.getTenantId();
        return (tenantId != null && !tenantId.isBlank())
                ? Optional.of(tenantId)
                : Optional.empty();
    }

    /**
     * Determines whether the given {@link Authentication} principal holds the
     * {@code ROLE_SUPER_ADMIN} authority.
     *
     * <p>Super admins are platform-level operators (e.g. Anthropic SREs or
     * BBSS operations team) that can view and manage resources across all
     * tenants.  Regular tenant administrators and end-users must never receive
     * this role.
     *
     * <p>A {@code null} or unauthenticated {@code Authentication} object is
     * treated as having no authorities and therefore returns {@code false}.
     *
     * @param auth the Spring Security {@link Authentication} for the current
     *             principal; may be {@code null}
     * @return {@code true} if the principal has {@code ROLE_SUPER_ADMIN},
     *         {@code false} otherwise
     */
    public boolean isSuperAdmin(Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) {
            return false;
        }
        return auth.getAuthorities()
                .stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(ROLE_SUPER_ADMIN::equals);
    }
}
