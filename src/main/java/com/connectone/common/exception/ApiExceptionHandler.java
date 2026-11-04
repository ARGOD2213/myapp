package com.connectone.common.exception;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.connectone.common.web.CorrelationIdFilter;

/**
 * Single place that turns exceptions into the JSON error contract:
 * {timestamp,status,error,category,message,correlationId,path?}. The category is a stable classification
 * (VALIDATION, AUTHENTICATION, AUTHORIZATION, NOT_FOUND, CONFLICT, BUSINESS_RULE, INTERNAL) used by support
 * engineers and the frontend; unexpected failures are logged with a stack trace and the correlation id but never
 * leak internals to the client.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Map<String, Object>> handleApiException(ApiException exception) {
        LOGGER.info("event=api_error status={} message=\"{}\"", exception.getStatus().value(), exception.getMessage());
        return response(exception.getStatus(), exception.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationException(
            MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getField() + " " + error.getDefaultMessage())
                .orElse("Request validation failed");
        return response(HttpStatus.BAD_REQUEST, message);
    }

    @ExceptionHandler(jakarta.validation.ConstraintViolationException.class)
    public ResponseEntity<Map<String, Object>> handleMethodValidationException(
            jakarta.validation.ConstraintViolationException exception) {
        return response(HttpStatus.BAD_REQUEST, "Request parameter validation failed");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> handleDataConflict(
            DataIntegrityViolationException exception) {
        LOGGER.warn("event=data_integrity_violation cause=\"{}\"",
                exception.getMostSpecificCause().getClass().getSimpleName());
        return response(HttpStatus.CONFLICT, "The request conflicts with existing data");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleUnexpected(Exception exception) throws Exception {
        if (exception instanceof AccessDeniedException || exception instanceof AuthenticationException) {
            throw exception; // let the security filter chain produce the 401/403 contract
        }
        if (exception instanceof ErrorResponse framework) { // Spring MVC's own 4xx (malformed JSON, bad types...)
            HttpStatusCode code = framework.getStatusCode();
            return response(HttpStatus.valueOf(code.value()),
                    code.is4xxClientError() ? "The request could not be processed" : "Unexpected server error");
        }
        LOGGER.error("event=unhandled_exception type={}", exception.getClass().getName(), exception);
        return response(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");
    }

    public static String categoryOf(int status) {
        return switch (status) {
            case 400 -> "VALIDATION";
            case 401 -> "AUTHENTICATION";
            case 403 -> "AUTHORIZATION";
            case 404 -> "NOT_FOUND";
            case 409, 422 -> "CONFLICT";
            default -> status >= 500 ? "INTERNAL" : "BUSINESS_RULE";
        };
    }

    private ResponseEntity<Map<String, Object>> response(HttpStatus status, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("category", categoryOf(status.value()));
        body.put("message", message);
        body.put("correlationId", MDC.get(CorrelationIdFilter.MDC_KEY));
        return ResponseEntity.status(status).body(body);
    }
}
