package com.bbss.shared.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Standardised error response body returned by {@link com.bbss.shared.exception.GlobalExceptionHandler}
 * for every non-2xx HTTP response emitted by BBSS microservices.
 *
 * <p>The {@code validationErrors} field is only serialised when it contains at
 * least one entry; all other {@code null} fields are suppressed by
 * {@link JsonInclude#NON_NULL}.
 *
 * <pre>{@code
 * ErrorResponse body = ErrorResponse.builder()
 *     .status(HttpStatus.NOT_FOUND.value())
 *     .error("Not Found")
 *     .message("Transaction 42 not found")
 *     .timestamp(LocalDateTime.now())
 *     .path("/api/v1/transactions/42")
 *     .build();
 * }</pre>
 */
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class ErrorResponse {

    /**
     * HTTP status code (e.g. 400, 403, 404, 500).
     * Mirrors the HTTP response status line for clients that read the body only.
     */
    private final int status;

    /**
     * Short, human-readable status phrase matching the HTTP status code
     * (e.g. "Bad Request", "Forbidden", "Internal Server Error").
     */
    private final String error;

    /**
     * Detailed description of what went wrong.
     * Safe to surface to API consumers; must not include stack traces or
     * internal implementation details.
     */
    private final String message;

    /**
     * Server-side instant at which the error was generated.
     * Serialised as an ISO-8601 string via the registered JSR-310 Jackson module.
     */
    private final LocalDateTime timestamp;

    /**
     * The request URI that triggered the error (e.g. {@code /api/v1/tenants/xyz}).
     * Populated from {@code HttpServletRequest#getRequestURI()} inside the exception handler.
     */
    private final String path;

    /**
     * Field-level validation error messages produced by Bean Validation
     * (JSR-380 / {@code @Valid}).
     *
     * <p>Each entry follows the format {@code "fieldName: constraint message"}.
     * This field is {@code null} (and therefore omitted from JSON) for all
     * error types other than 400 validation failures.
     */
    private final List<String> validationErrors;
}
