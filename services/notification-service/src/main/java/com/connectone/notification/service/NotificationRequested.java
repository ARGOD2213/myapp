package com.connectone.notification.service;

/**
 * Consumer-side view of the {@code notification.requested} event (schema v1, see docs/events/notification-requested.v1.json).
 * Deliberately NOT shared as a library with core-service: each service owns its own reading of the contract, and the contract
 * test in this module is what protects both sides. Unknown extra fields are ignored (forward compatible).
 */
public record NotificationRequested(
        String eventId,
        Integer schemaVersion,
        long customerId,
        Long userId,
        String type,
        String subject,
        String message,
        String idempotencyKey) {

    public NotificationRequested {
        if (type == null || type.isBlank() || message == null || message.isBlank() || idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("notification.requested is missing type, message or idempotencyKey");
        }
    }
}
