package com.bbss.shared.exception;

/**
 * Thrown when a requested resource does not exist in the data store or is
 * not visible to the current tenant context.
 *
 * <p>The {@link com.bbss.shared.exception.GlobalExceptionHandler} maps this
 * exception to HTTP 404 Not Found.
 *
 * <p>Usage:
 * <pre>{@code
 * // Descriptive message only
 * throw new ResourceNotFoundException("Transaction 42 not found");
 *
 * // With chained cause
 * throw new ResourceNotFoundException("Account lookup failed", cause);
 * }</pre>
 *
 * <p>Subclasses may provide domain-specific constructors that automatically
 * format the message from an entity type and identifier; see
 * {@link TenantNotFoundException} for an example.
 */
public class ResourceNotFoundException extends RuntimeException {

    /**
     * Constructs a new {@code ResourceNotFoundException} with the specified
     * detail message.
     *
     * @param message human-readable description of which resource was not found;
     *                should include enough context (entity type and id) for the
     *                API consumer to understand what was missing
     */
    public ResourceNotFoundException(String message) {
        super(message);
    }

    /**
     * Constructs a new {@code ResourceNotFoundException} with the specified
     * detail message and a root cause.
     *
     * @param message human-readable description of which resource was not found
     * @param cause   the underlying exception that triggered this error
     *                (e.g. a database or remote call failure)
     */
    public ResourceNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
