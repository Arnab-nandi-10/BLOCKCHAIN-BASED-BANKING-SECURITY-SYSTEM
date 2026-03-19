package com.bbss.shared.exception;

/**
 * Specialised {@link ResourceNotFoundException} thrown when a tenant cannot
 * be located by its identifier.
 *
 * <p>This class exists to make exception handling and log analysis easier:
 * callers can catch {@code TenantNotFoundException} specifically when they
 * need to distinguish a missing-tenant scenario from other missing-resource
 * scenarios, or they can catch the parent {@code ResourceNotFoundException}
 * to treat all 404-class errors uniformly.
 *
 * <p>The message is automatically formatted as:
 * <pre>
 *   Tenant not found: [tenantId]
 * </pre>
 *
 * <p>Usage:
 * <pre>{@code
 * Tenant tenant = tenantRepository.findById(tenantId)
 *     .orElseThrow(() -> new TenantNotFoundException(tenantId));
 * }</pre>
 */
public class TenantNotFoundException extends ResourceNotFoundException {

    /**
     * Constructs a {@code TenantNotFoundException} for the given tenant
     * identifier.
     *
     * <p>The detail message is formatted as
     * {@code "Tenant not found: <tenantId>"}.
     *
     * @param tenantId the identifier of the tenant that could not be found;
     *                 included verbatim in the exception message and therefore
     *                 in the HTTP 404 response body returned to the caller
     */
    public TenantNotFoundException(String tenantId) {
        super("Tenant not found: " + tenantId);
    }
}
