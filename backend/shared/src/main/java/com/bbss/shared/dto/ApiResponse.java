package com.bbss.shared.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Generic API response envelope used by every REST endpoint in the Civic Savings platform.
 *
 * <p>Callers use the static factory methods rather than the Lombok builder directly
 * so that {@code timestamp} and {@code requestId} are always populated consistently.
 *
 * <pre>{@code
 * // Success with data
 * return ResponseEntity.ok(ApiResponse.success(transactionDto));
 *
 * // Success with custom message and data
 * return ResponseEntity.ok(ApiResponse.success("Transaction created", transactionDto));
 *
 * // Error
 * return ResponseEntity.badRequest().body(ApiResponse.error("Invalid account number"));
 * }</pre>
 *
 * @param <T> type of the {@code data} payload
 */
@Getter
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class ApiResponse<T> {

    /** {@code true} when the request was processed without errors. */
    private final boolean success;

    /** Human-readable description of the result. */
    private final String message;

    /**
     * Response payload; {@code null} for error responses or operations that
     * intentionally return no body (e.g. DELETE).
     */
    private final T data;

    /** Server-side timestamp at which this response was assembled. */
    private final LocalDateTime timestamp;

    /**
     * Correlation identifier forwarded from the inbound request header
     * {@code X-Request-Id}, or a freshly generated UUID when the header is absent.
     */
    private final String requestId;

    // ── Lombok @Builder requires an all-args constructor ─────────────────────

    @Builder
    private ApiResponse(
            boolean success,
            String message,
            T data,
            LocalDateTime timestamp,
            String requestId) {
        this.success   = success;
        this.message   = message;
        this.data      = data;
        this.timestamp = timestamp != null ? timestamp : LocalDateTime.now();
        this.requestId = requestId != null ? requestId : UUID.randomUUID().toString();
    }

    // ── Static factory methods ────────────────────────────────────────────────

    /**
     * Creates a successful response wrapping the given data payload.
     * The {@code message} field is omitted ({@code null}).
     *
     * @param <T>  payload type
     * @param data the response body
     * @return a success {@code ApiResponse}
     */
    public static <T> ApiResponse<T> success(T data) {
        return ApiResponse.<T>builder()
                .success(true)
                .data(data)
                .timestamp(LocalDateTime.now())
                .requestId(UUID.randomUUID().toString())
                .build();
    }

    /**
     * Creates a successful response with an explicit message and a data payload.
     *
     * @param <T>     payload type
     * @param message human-readable description
     * @param data    the response body
     * @return a success {@code ApiResponse}
     */
    public static <T> ApiResponse<T> success(String message, T data) {
        return ApiResponse.<T>builder()
                .success(true)
                .message(message)
                .data(data)
                .timestamp(LocalDateTime.now())
                .requestId(UUID.randomUUID().toString())
                .build();
    }

    /**
     * Creates an error response with no data payload.
     *
     * @param <T>     inferred payload type (will be {@code null})
     * @param message human-readable error description
     * @return an error {@code ApiResponse}
     */
    public static <T> ApiResponse<T> error(String message) {
        return ApiResponse.<T>builder()
                .success(false)
                .message(message)
                .timestamp(LocalDateTime.now())
                .requestId(UUID.randomUUID().toString())
                .build();
    }

    /**
     * Creates an error response that also carries a data payload (e.g. partial
     * results or additional diagnostic information).
     *
     * @param <T>     payload type
     * @param message human-readable error description
     * @param data    supplementary payload
     * @return an error {@code ApiResponse}
     */
    public static <T> ApiResponse<T> error(String message, T data) {
        return ApiResponse.<T>builder()
                .success(false)
                .message(message)
                .data(data)
                .timestamp(LocalDateTime.now())
                .requestId(UUID.randomUUID().toString())
                .build();
    }
}
