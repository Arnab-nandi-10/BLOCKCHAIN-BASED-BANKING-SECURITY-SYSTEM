package com.bbss.shared.exception;

import lombok.Getter;

/**
 * Thrown when a request violates a domain or business rule that cannot be
 * expressed as a Bean Validation constraint.
 *
 * <p>Examples:
 * <ul>
 *   <li>Attempting a transfer with insufficient funds</li>
 *   <li>Opening a duplicate account for the same customer</li>
 *   <li>Approving a transaction that has already been rejected</li>
 *   <li>Exceeding a tenant's daily transaction limit</li>
 * </ul>
 *
 * <p>The {@link GlobalExceptionHandler} maps this exception to HTTP
 * 400 Bad Request.  The {@link #errorCode} is prepended to the response
 * message so that API consumers can programmatically distinguish between
 * different business failures without parsing the human-readable message.
 *
 * <p>Usage:
 * <pre>{@code
 * // With error code and message
 * throw new BusinessException("Insufficient funds for account " + accountId,
 *                              "INSUFFICIENT_FUNDS");
 *
 * // Message only (errorCode will be null)
 * throw new BusinessException("Duplicate account detected");
 * }</pre>
 */
@Getter
public class BusinessException extends RuntimeException {

    /**
     * Machine-readable error code that identifies the specific business rule
     * that was violated.
     *
     * <p>Convention: {@code SCREAMING_SNAKE_CASE} string constant defined by
     * the owning service (e.g. {@code "INSUFFICIENT_FUNDS"},
     * {@code "DAILY_LIMIT_EXCEEDED"}, {@code "DUPLICATE_ACCOUNT"}).
     *
     * <p>May be {@code null} when the caller does not provide a code; the
     * {@link GlobalExceptionHandler} will still produce a valid response.
     */
    private final String errorCode;

    /**
     * Constructs a {@code BusinessException} with a detail message and a
     * machine-readable error code.
     *
     * @param message   human-readable description of the business rule violation;
     *                  safe to surface to the API consumer
     * @param errorCode {@code SCREAMING_SNAKE_CASE} identifier of the violated rule
     */
    public BusinessException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
    }

    /**
     * Constructs a {@code BusinessException} with a detail message only.
     * The {@link #errorCode} will be {@code null}.
     *
     * @param message human-readable description of the business rule violation
     */
    public BusinessException(String message) {
        super(message);
        this.errorCode = null;
    }

    /**
     * Constructs a {@code BusinessException} with a detail message, an error
     * code, and a root cause.
     *
     * @param message   human-readable description of the business rule violation
     * @param errorCode {@code SCREAMING_SNAKE_CASE} identifier of the violated rule
     * @param cause     the underlying exception that triggered this error
     */
    public BusinessException(String message, String errorCode, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }
}
