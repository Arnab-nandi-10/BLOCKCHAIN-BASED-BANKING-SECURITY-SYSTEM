package com.bbss.shared.exception;

import com.bbss.shared.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Centralised exception handler for all BBSS REST controllers.
 *
 * <p>This advice intercepts exceptions thrown anywhere in the controller or
 * service layer and converts them into standardised {@link ErrorResponse} JSON
 * bodies, preventing raw stack traces from leaking to API consumers.
 *
 * <p>Ordering of handlers (most specific first):
 * <ol>
 *   <li>{@link ResourceNotFoundException}  → 404 Not Found</li>
 *   <li>{@link BusinessException}           → 400 Bad Request</li>
 *   <li>{@link AccessDeniedException}       → 403 Forbidden</li>
 *   <li>{@link MethodArgumentNotValidException} → 400 Bad Request (with field errors)</li>
 *   <li>{@link HttpMessageNotReadableException} → 400 Bad Request</li>
 *   <li>{@link Exception} (catch-all)       → 500 Internal Server Error</li>
 * </ol>
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // ── 404 Not Found ─────────────────────────────────────────────────────────

    /**
     * Handles {@link ResourceNotFoundException} thrown when a requested entity
     * does not exist (or is not visible to the current tenant).
     *
     * @param ex      the exception
     * @param request the current HTTP request (used to populate {@code path})
     * @return 404 response with error details
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleResourceNotFound(
            ResourceNotFoundException ex,
            HttpServletRequest request) {

        log.warn("Resource not found [path={}]: {}", request.getRequestURI(), ex.getMessage());

        ErrorResponse body = ErrorResponse.builder()
                .status(HttpStatus.NOT_FOUND.value())
                .error(HttpStatus.NOT_FOUND.getReasonPhrase())
                .message(ex.getMessage())
                .timestamp(LocalDateTime.now())
                .path(request.getRequestURI())
                .build();

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }

    // ── 400 Bad Request – domain / business rule violation ────────────────────

    /**
     * Handles {@link BusinessException} thrown when a request violates a
     * domain or business rule (e.g. insufficient funds, duplicate account).
     *
     * @param ex      the exception
     * @param request the current HTTP request
     * @return 400 response with the business error code and message
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(
            BusinessException ex,
            HttpServletRequest request) {

        log.warn("Business rule violation [errorCode={}, path={}]: {}",
                ex.getErrorCode(), request.getRequestURI(), ex.getMessage());

        // Prepend the error code to the message for easier client-side parsing.
        String detail = ex.getErrorCode() != null
                ? "[" + ex.getErrorCode() + "] " + ex.getMessage()
                : ex.getMessage();

        ErrorResponse body = ErrorResponse.builder()
                .status(HttpStatus.BAD_REQUEST.value())
                .error(HttpStatus.BAD_REQUEST.getReasonPhrase())
                .message(detail)
                .timestamp(LocalDateTime.now())
                .path(request.getRequestURI())
                .build();

        return ResponseEntity.badRequest().body(body);
    }

    // ── 403 Forbidden ─────────────────────────────────────────────────────────

    /**
     * Handles Spring Security's {@link AccessDeniedException} raised when an
     * authenticated user attempts an operation they are not authorised to perform.
     *
     * <p>Note: this handler only fires when Spring Security is configured to
     * delegate {@code AccessDeniedException} to the MVC exception handler rather
     * than to its own {@code AccessDeniedHandler}.  In a typical JWT stateless
     * setup both paths may be active; ensure the security filter chain is
     * configured appropriately.
     *
     * @param ex      the exception
     * @param request the current HTTP request
     * @return 403 response
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(
            AccessDeniedException ex,
            HttpServletRequest request) {

        log.warn("Access denied [path={}]: {}", request.getRequestURI(), ex.getMessage());

        ErrorResponse body = ErrorResponse.builder()
                .status(HttpStatus.FORBIDDEN.value())
                .error(HttpStatus.FORBIDDEN.getReasonPhrase())
                .message("You do not have permission to perform this action.")
                .timestamp(LocalDateTime.now())
                .path(request.getRequestURI())
                .build();

        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body);
    }

    // ── 400 Bad Request – Bean Validation ────────────────────────────────────

    /**
     * Handles constraint violations raised by {@code @Valid} / {@code @Validated}
     * on controller method parameters.
     *
     * <p>Each field error is formatted as {@code "fieldName: constraint message"}
     * and collected into the {@code validationErrors} list of the response body.
     *
     * @param ex      the exception carrying all constraint violation details
     * @param request the current HTTP request
     * @return 400 response with a list of field-level error descriptions
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationErrors(
            MethodArgumentNotValidException ex,
            HttpServletRequest request) {

        List<String> fieldErrors = ex.getBindingResult()
                .getAllErrors()
                .stream()
                .map(error -> {
                    if (error instanceof FieldError fe) {
                        return fe.getField() + ": " + fe.getDefaultMessage();
                    }
                    return error.getObjectName() + ": " + error.getDefaultMessage();
                })
                .sorted()
                .collect(Collectors.toList());

        log.warn("Validation failed [path={}, errors={}]: {}",
                request.getRequestURI(), fieldErrors.size(), fieldErrors);

        ErrorResponse body = ErrorResponse.builder()
                .status(HttpStatus.BAD_REQUEST.value())
                .error(HttpStatus.BAD_REQUEST.getReasonPhrase())
                .message("Request validation failed. See 'validationErrors' for details.")
                .timestamp(LocalDateTime.now())
                .path(request.getRequestURI())
                .validationErrors(fieldErrors)
                .build();

        return ResponseEntity.badRequest().body(body);
    }

    // ── 400 Bad Request – malformed JSON ─────────────────────────────────────

    /**
     * Handles {@link HttpMessageNotReadableException} raised when the request
     * body is malformed JSON or is missing entirely for endpoints that require
     * a body.
     *
     * @param ex      the exception
     * @param request the current HTTP request
     * @return 400 response with a generic malformed-input message
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex,
            HttpServletRequest request) {

        log.warn("Malformed request body [path={}]: {}", request.getRequestURI(), ex.getMessage());

        ErrorResponse body = ErrorResponse.builder()
                .status(HttpStatus.BAD_REQUEST.value())
                .error(HttpStatus.BAD_REQUEST.getReasonPhrase())
                .message("Malformed or missing request body. Please verify the JSON payload.")
                .timestamp(LocalDateTime.now())
                .path(request.getRequestURI())
                .build();

        return ResponseEntity.badRequest().body(body);
    }

    // ── 500 Internal Server Error – catch-all ────────────────────────────────

    /**
     * Fallback handler for any unexpected {@link Exception} not matched by the
     * more specific handlers above.
     *
     * <p>The full stack trace is logged at ERROR level but is <em>never</em>
     * included in the response body to avoid leaking implementation details.
     *
     * @param ex      the exception
     * @param request the current HTTP request
     * @return 500 response with a generic error message
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(
            Exception ex,
            HttpServletRequest request) {

        log.error("Unhandled exception [path={}]", request.getRequestURI(), ex);

        ErrorResponse body = ErrorResponse.builder()
                .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .error(HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase())
                .message("An unexpected error occurred. Please try again later or contact support.")
                .timestamp(LocalDateTime.now())
                .path(request.getRequestURI())
                .build();

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }
}
