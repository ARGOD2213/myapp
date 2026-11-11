package com.connectone.notification.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.connectone.notification.web.ApiExceptionHandler.NotFound;

/** All persistence and delivery logic of the notification domain. */
@Service
public class NotificationStore {

    static final int MAX_ATTEMPTS = 5;

    private final JdbcTemplate jdbc;
    private final boolean simulatedFailure;

    public NotificationStore(JdbcTemplate jdbc, @Value("${connectone.notification.simulated-failure:false}") boolean simulatedFailure) {
        this.jdbc = jdbc;
        this.simulatedFailure = simulatedFailure;
    }

    /**
     * Idempotent consumer: the unique index (customer_id, idempotency_key) makes a re-delivered event a no-op.
     * @return true when a new notification was stored (and a delivery attempt made), false for a duplicate
     */
    @Transactional
    public boolean accept(NotificationRequested e) {
        List<Long> ids = jdbc.query(
                "INSERT INTO notification_record (event_id, customer_id, user_id, channel, event_type, status, subject, message, idempotency_key) "
                        + "VALUES (?, ?, ?, 'IN_APP', ?, 'PENDING', ?, ?, ?) ON CONFLICT (customer_id, idempotency_key) DO NOTHING RETURNING id",
                (rs, n) -> rs.getLong(1), e.eventId(), e.customerId(), e.userId(), e.type(), e.subject(), e.message(), e.idempotencyKey());
        if (ids.isEmpty()) {
            return false;
        }
        deliver(ids.get(0));
        return true;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> history(long userId) {
        return camel(jdbc.queryForList(
                "SELECT id, channel, event_type, status, subject, message, read_at, created_at, delivered_at "
                        + "FROM notification_record WHERE user_id = ? ORDER BY created_at DESC, id DESC LIMIT 200", userId));
    }

    @Transactional
    public void markRead(long userId, long id) {
        int updated = jdbc.update("UPDATE notification_record SET read_at = CURRENT_TIMESTAMP WHERE id = ? AND user_id = ? AND read_at IS NULL", id, userId);
        if (updated == 0) {
            Integer exists = jdbc.queryForObject("SELECT COUNT(*) FROM notification_record WHERE id = ? AND user_id = ?", Integer.class, id, userId);
            if (exists == null || exists == 0) {
                throw new NotFound("Notification was not found");
            }
        }
    }

    @Transactional(readOnly = true)
    public Map<String, Object> deliveries(String status, int page, int requestedSize) {
        int size = Math.min(Math.max(requestedSize, 1), 100);
        int number = Math.max(page, 0);
        String where = status == null || status.isBlank() ? "" : " WHERE status = ?";
        Object[] filter = where.isEmpty() ? new Object[0] : new Object[] {status};
        List<Object> args = new ArrayList<>(List.of(filter));
        args.add(size);
        args.add((long) number * size);
        List<Map<String, Object>> content = camel(jdbc.queryForList(
                "SELECT id, customer_id, channel, event_type, status, attempt_count, last_error, created_at, next_attempt_at "
                        + "FROM notification_record" + where + " ORDER BY created_at DESC, id DESC LIMIT ? OFFSET ?", args.toArray()));
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM notification_record" + where, Long.class, filter);
        long totalElements = total == null ? 0 : total;
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("content", content);
        result.put("page", Map.of("size", size, "number", number, "totalElements", totalElements, "totalPages", (totalElements + size - 1) / size));
        return result;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> summaryByStatus() {
        return camel(jdbc.queryForList("SELECT status AS key, COUNT(*) AS count FROM notification_record GROUP BY 1 ORDER BY 1"));
    }

    @Transactional
    public int retryFailed() {
        List<Long> ids = jdbc.query(
                "SELECT id FROM notification_record WHERE status = 'FAILED' AND attempt_count < " + MAX_ATTEMPTS
                        + " AND (next_attempt_at IS NULL OR next_attempt_at <= CURRENT_TIMESTAMP) ORDER BY created_at LIMIT 100",
                (rs, n) -> rs.getLong(1));
        ids.forEach(this::deliver);
        return ids.size();
    }

    @Scheduled(fixedDelayString = "${connectone.notification.retry-delay-ms:60000}")
    public void retryScheduled() {
        retryFailed();
    }

    /** Simulated provider: succeeds unless connectone.notification.simulated-failure=true. */
    private void deliver(long id) {
        Map<String, Object> row = jdbc.queryForMap("SELECT status, attempt_count FROM notification_record WHERE id = ? FOR UPDATE", id);
        int attempts = ((Number) row.get("attempt_count")).intValue();
        if (!List.of("PENDING", "FAILED").contains(row.get("status")) || attempts >= MAX_ATTEMPTS) {
            return;
        }
        if (!simulatedFailure) {
            jdbc.update("UPDATE notification_record SET status = 'SENT', attempt_count = attempt_count + 1, delivered_at = CURRENT_TIMESTAMP, "
                    + "next_attempt_at = NULL, last_error = NULL WHERE id = ?", id);
        } else {
            jdbc.update("UPDATE notification_record SET status = 'FAILED', attempt_count = attempt_count + 1, "
                    + "next_attempt_at = CURRENT_TIMESTAMP + INTERVAL '1' MINUTE, last_error = 'Simulated delivery failure' WHERE id = ?", id);
        }
    }

    static List<Map<String, Object>> camel(List<Map<String, Object>> rows) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> m = new LinkedHashMap<>();
            row.forEach((k, v) -> m.put(toCamel(k), v instanceof java.sql.Timestamp t ? t.toLocalDateTime() : v));
            out.add(m);
        }
        return out;
    }

    private static String toCamel(String snake) {
        StringBuilder sb = new StringBuilder();
        boolean up = false;
        for (char c : snake.toCharArray()) {
            if (c == '_') {
                up = true;
            } else {
                sb.append(up ? Character.toUpperCase(c) : c);
                up = false;
            }
        }
        return sb.toString();
    }
}
