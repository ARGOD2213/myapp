package com.connectone.notification.web;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Same JSON error contract as core-service: {timestamp,status,error,category,message,correlationId}. */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(ApiExceptionHandler.class);

    public static class NotFound extends RuntimeException {
        public NotFound(String message) {
            super(message);
        }
    }

    @ExceptionHandler(NotFound.class)
    ResponseEntity<Map<String, Object>> notFound(NotFound e) {
        return body(HttpStatus.NOT_FOUND, "NOT_FOUND", e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Map<String, Object>> other(Exception e) throws Exception {
        if (e instanceof AccessDeniedException || e instanceof AuthenticationException) {
            throw e;
        }
        if (e instanceof ErrorResponse framework) {
            HttpStatusCode code = framework.getStatusCode();
            return body(HttpStatus.valueOf(code.value()), code.is4xxClientError() ? "VALIDATION" : "INTERNAL", "The request could not be processed");
        }
        LOGGER.error("event=unhandled_exception type={}", e.getClass().getName(), e);
        return body(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL", "An unexpected error occurred");
    }

    private ResponseEntity<Map<String, Object>> body(HttpStatus status, String category, String message) {
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("timestamp", Instant.now().toString());
        b.put("status", status.value());
        b.put("error", status.getReasonPhrase());
        b.put("category", category);
        b.put("message", message);
        b.put("correlationId", MDC.get("correlationId"));
        return ResponseEntity.status(status).body(b);
    }
}
