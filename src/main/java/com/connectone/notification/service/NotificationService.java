package com.connectone.notification.service;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.connectone.common.database.GeneratedKeySupport;
import com.connectone.common.database.JdbcRowSupport;
import com.connectone.common.exception.ApiException;

@Service
public class NotificationService {

    private final JdbcTemplate jdbcTemplate;
    private final NotificationProvider provider;

    public NotificationService(JdbcTemplate jdbcTemplate, NotificationProvider provider) {
        this.jdbcTemplate = jdbcTemplate;
        this.provider = provider;
    }

    @Transactional
    public void enqueue(long customerId, String type, String subject, String message, String idempotencyKey) {
        Integer exists = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM notification_record WHERE customer_id = ? AND idempotency_key = ?",
                Integer.class, customerId, idempotencyKey);
        if (exists != null && exists > 0) {
            return;
        }
        long id = GeneratedKeySupport.insert(jdbcTemplate,
                "INSERT INTO notification_record "
                        + "(customer_id, channel, event_type, status, subject, message, idempotency_key) "
                        + "VALUES (?, 'IN_APP', ?, 'PENDING', ?, ?, ?)",
                customerId, type, subject, message, idempotencyKey);
        deliver(id);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> history(long userId) {
        long customerId = customerId(userId);
        return JdbcRowSupport.camelCaseRows(jdbcTemplate.queryForList(
                "SELECT id, channel, event_type, status, subject, message, read_at, created_at, delivered_at "
                        + "FROM notification_record WHERE customer_id = ? ORDER BY created_at DESC, id DESC LIMIT 200",
                customerId));
    }

    @Transactional
    public void markRead(long userId, long notificationId) {
        int updated = jdbcTemplate.update(
                "UPDATE notification_record SET read_at = CURRENT_TIMESTAMP "
                        + "WHERE id = ? AND customer_id = ? AND read_at IS NULL",
                notificationId, customerId(userId));
        if (updated == 0) {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM notification_record WHERE id = ? AND customer_id = ?",
                    Integer.class, notificationId, customerId(userId));
            if (count == null || count == 0) {
                throw new ApiException(HttpStatus.NOT_FOUND, "Notification was not found");
            }
        }
    }

    @Transactional
    public int retryFailed() {
        List<Long> ids = jdbcTemplate.query(
                "SELECT id FROM notification_record WHERE status = 'FAILED' AND attempt_count < 5 "
                        + "AND (next_attempt_at IS NULL OR next_attempt_at <= CURRENT_TIMESTAMP) "
                        + "ORDER BY created_at LIMIT 100",
                (result, row) -> result.getLong(1));
        ids.forEach(this::deliver);
        return ids.size();
    }

    @Scheduled(fixedDelayString = "${connectone.notification.retry-delay-ms:60000}")
    @Transactional
    public void retryScheduled() {
        retryFailed();
    }

    private void deliver(long id) {
        Map<String, Object> notification = jdbcTemplate.queryForMap(
                "SELECT status, attempt_count FROM notification_record WHERE id = ? FOR UPDATE", id);
        String status = (String) notification.get("status");
        int attempts = ((Number) notification.get("attempt_count")).intValue();
        if (!"PENDING".equals(status) && !"FAILED".equals(status) || attempts >= 5) {
            return;
        }
        if (provider.deliver()) {
            jdbcTemplate.update(
                    "UPDATE notification_record SET status = 'SENT', attempt_count = attempt_count + 1, "
                            + "delivered_at = CURRENT_TIMESTAMP, next_attempt_at = NULL, last_error = NULL WHERE id = ?",
                    id);
        } else {
            jdbcTemplate.update(
                    "UPDATE notification_record SET status = 'FAILED', attempt_count = attempt_count + 1, "
                            + "next_attempt_at = CURRENT_TIMESTAMP + INTERVAL '1' MINUTE, "
                            + "last_error = 'Simulated delivery failure' WHERE id = ?",
                    id);
        }
    }

    private long customerId(long userId) {
        Long id = jdbcTemplate.query(
                "SELECT id FROM customer_profile WHERE user_id = ? AND account_status = 'ACTIVE'",
                result -> result.next() ? result.getLong(1) : null, userId);
        if (id == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Customer profile was not found");
        }
        return id;
    }
}
