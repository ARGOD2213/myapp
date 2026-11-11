package com.connectone.notification.service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.connectone.common.outbox.OutboxService;

/**
 * Core-service's side of notifications: it no longer stores or delivers anything. A business flow calls
 * {@link #enqueue}, which writes a {@code notification.requested} event to the outbox in the caller's transaction.
 * notification-service (separate deployable, own database) consumes it from Kafka, stores it idempotently and delivers.
 * The signature is unchanged so the ~15 call sites in payment, booking, subscription, support... did not change.
 */
@Service
public class NotificationService {

    public static final String EVENT_TYPE = "notification.requested";

    private final JdbcTemplate jdbcTemplate;
    private final OutboxService outbox;

    public NotificationService(JdbcTemplate jdbcTemplate, OutboxService outbox) {
        this.jdbcTemplate = jdbcTemplate;
        this.outbox = outbox;
    }

    @Transactional
    public void enqueue(long customerId, String type, String subject, String message, String idempotencyKey) {
        List<Long> users = jdbcTemplate.queryForList("SELECT user_id FROM customer_profile WHERE id = ?", Long.class, customerId);
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("eventId", UUID.randomUUID().toString());
        event.put("schemaVersion", 1);
        event.put("occurredAt", Instant.now().toString());
        event.put("customerId", customerId);
        event.put("userId", users.isEmpty() ? null : users.get(0));
        event.put("type", type);
        event.put("subject", subject);
        event.put("message", message);
        event.put("idempotencyKey", idempotencyKey);
        outbox.publish("customer", Long.toString(customerId), EVENT_TYPE, event);
    }
}
