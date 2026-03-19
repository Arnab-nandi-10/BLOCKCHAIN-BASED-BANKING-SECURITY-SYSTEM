package com.bbss.shared.dto;

/**
 * Thread-local holder for the current tenant identifier.
 *
 * <p>In a multi-tenant system every inbound HTTP request carries a tenant
 * discriminator (typically in a JWT claim or a custom header such as
 * {@code X-Tenant-Id}).  The API Gateway or a servlet filter extracts that
 * value and calls {@link #setTenantId(String)} so that any downstream
 * component – repository queries, Kafka producers, audit writers – can
 * retrieve it without requiring it to be threaded explicitly through every
 * method signature.
 *
 * <p><strong>Lifecycle contract</strong>: the tenant context MUST be cleared
 * at the end of each request to prevent leakage to subsequent requests that
 * reuse the same thread from the container thread pool.  The preferred pattern
 * is a try-with-resources block because {@code TenantContext} implements
 * {@link AutoCloseable}:
 *
 * <pre>{@code
 * try (TenantContext ctx = TenantContext.set("acme-bank")) {
 *     // all code here sees "acme-bank" as the current tenant
 * }
 * // context has been cleared automatically
 * }</pre>
 *
 * Alternatively, a servlet filter may call {@link #clear()} in a
 * {@code finally} block.
 *
 * <p>This class is intentionally {@code final} and has no public constructor;
 * it exposes only static utility methods plus the {@link AutoCloseable} contract.
 */
public final class TenantContext implements AutoCloseable {

    /**
     * Backing thread-local storage.  Uses {@link InheritableThreadLocal} so
     * that child threads spawned during request processing (e.g. async
     * {@code @Async} methods) automatically inherit the parent's tenant value.
     *
     * <p>Callers that spawn their own thread pools must propagate the value
     * manually via {@link #setTenantId(String)} if inheritance is not
     * guaranteed by the executor implementation.
     */
    private static final InheritableThreadLocal<String> CONTEXT =
            new InheritableThreadLocal<>();

    // ── Construction ──────────────────────────────────────────────────────────

    /**
     * Private constructor – this class is not meant to be instantiated
     * directly.  Use {@link #set(String)} when a try-with-resources scope
     * is required, or the bare {@link #setTenantId(String)} / {@link #clear()}
     * pair for manual lifecycle management.
     */
    private TenantContext() {
        // non-instantiable utility class
    }

    // ── Static API ────────────────────────────────────────────────────────────

    /**
     * Stores {@code tenantId} in the current thread's context.
     *
     * @param tenantId the tenant identifier to bind; must not be {@code null}
     *                 or blank in production paths
     */
    public static void setTenantId(String tenantId) {
        CONTEXT.set(tenantId);
    }

    /**
     * Returns the tenant identifier bound to the current thread, or
     * {@code null} if none has been set (e.g. during startup or in
     * background tasks that execute without a tenant scope).
     *
     * @return current tenant id, possibly {@code null}
     */
    public static String getTenantId() {
        return CONTEXT.get();
    }

    /**
     * Removes the tenant identifier from the current thread, resetting
     * the context to an empty state.
     *
     * <p>This method is idempotent; calling it when no value has been set
     * is a no-op.
     */
    public static void clear() {
        CONTEXT.remove();
    }

    /**
     * Convenience factory that sets the given tenant id and returns a
     * {@code TenantContext} instance whose {@link #close()} method will
     * call {@link #clear()}.  Designed for use with try-with-resources.
     *
     * @param tenantId the tenant identifier to bind
     * @return a closeable scope object
     */
    public static TenantContext set(String tenantId) {
        setTenantId(tenantId);
        return new TenantContext();
    }

    // ── AutoCloseable ─────────────────────────────────────────────────────────

    /**
     * Clears the tenant context bound to the current thread.
     * Invoked automatically at the end of a try-with-resources block.
     */
    @Override
    public void close() {
        clear();
    }
}
