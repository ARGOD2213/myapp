package com.connectone.admin.service;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.connectone.common.audit.AuditService;
import com.connectone.common.database.GeneratedKeySupport;
import com.connectone.common.database.JdbcRowSupport;
import com.connectone.common.exception.ApiException;
import com.connectone.common.metrics.BusinessMetrics;
import com.connectone.notification.service.NotificationService;

/** Back-office reads and operations: dashboard, customer 360, payment/refund monitoring, reconciliation, catalogues. */
@Service
public class AdminOperationsService {

    private final JdbcTemplate jdbcTemplate;
    private final AuditService audit;
    private final NotificationService notifications;
    private final CheckoutCouponService coupons;
    private final BusinessMetrics metrics;

    public AdminOperationsService(JdbcTemplate jdbcTemplate, AuditService audit, NotificationService notifications,
            CheckoutCouponService coupons, BusinessMetrics metrics) {
        this.jdbcTemplate = jdbcTemplate;
        this.audit = audit;
        this.notifications = notifications;
        this.coupons = coupons;
        this.metrics = metrics;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> dashboard() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("customersByStatus", rows("SELECT account_status AS key, COUNT(*) AS count FROM customer_profile GROUP BY 1 ORDER BY 1"));
        result.put("paymentsLast24h", rows("SELECT status AS key, COUNT(*) AS count FROM payment_record "
                + "WHERE created_at > CURRENT_TIMESTAMP - INTERVAL '24 hours' GROUP BY 1 ORDER BY 1"));
        result.put("revenueLast30Days", jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(amount), 0) FROM payment_record WHERE status IN ('SUCCEEDED') "
                        + "AND created_at > CURRENT_TIMESTAMP - INTERVAL '30 days'", java.math.BigDecimal.class));
        result.put("refundedLast30Days", jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(amount), 0) FROM refund_record WHERE status = 'SUCCEEDED' "
                        + "AND created_at > CURRENT_TIMESTAMP - INTERVAL '30 days'", java.math.BigDecimal.class));
        result.put("paymentsNeedingReconciliation", jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM payment_record WHERE reconciliation_state IN ('RETRY_REQUIRED', 'RECONCILIATION_REQUIRED')",
                Long.class));
        result.put("subscriptionsByStatus", rows("SELECT status AS key, COUNT(*) AS count FROM customer_subscription GROUP BY 1 ORDER BY 1"));
        result.put("ticketsByStatus", rows("SELECT status AS key, COUNT(*) AS count FROM support_ticket GROUP BY 1 ORDER BY 1"));
        result.put("staleInProgressTickets", jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM support_ticket WHERE status = 'IN_PROGRESS' AND updated_at < CURRENT_TIMESTAMP - INTERVAL '14 days'",
                Long.class));
        result.put("outboxPending", jdbcTemplate.queryForObject("SELECT COUNT(*) FROM outbox_event WHERE published_at IS NULL", Long.class));
        result.put("upcomingBookings", jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM booking_record b JOIN booking_catalog_item i ON i.id = b.catalog_item_id "
                        + "WHERE b.status = 'CONFIRMED' AND i.starts_at > CURRENT_TIMESTAMP", Long.class));
        return result;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> customerDetail(long customerId) {
        Map<String, Object> profile;
        try {
            profile = JdbcRowSupport.camelCaseRow(jdbcTemplate.queryForMap(
                    "SELECT c.id, u.email, c.first_name, c.last_name, c.phone_number, c.account_status, "
                            + "u.account_status AS login_status, u.failed_login_attempts, u.locked_until, c.created_at "
                            + "FROM customer_profile c JOIN app_user u ON u.id = c.user_id WHERE c.id = ?", customerId));
        } catch (EmptyResultDataAccessException exception) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Customer was not found");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("profile", profile);
        result.put("mobileNumbers", rows("SELECT id, phone_number, status, is_primary FROM mobile_number WHERE customer_id = ? ORDER BY id", customerId));
        result.put("totals", JdbcRowSupport.camelCaseRow(jdbcTemplate.queryForMap(
                "SELECT (SELECT COUNT(*) FROM recharge_transaction WHERE customer_id = ?) AS recharges, "
                        + "(SELECT COUNT(*) FROM payment_record WHERE customer_id = ?) AS payments, "
                        + "(SELECT COALESCE(SUM(amount), 0) FROM payment_record WHERE customer_id = ? AND status = 'SUCCEEDED') AS paid_amount, "
                        + "(SELECT COUNT(*) FROM booking_record WHERE customer_id = ?) AS bookings, "
                        + "(SELECT COUNT(*) FROM support_ticket WHERE customer_id = ?) AS tickets",
                customerId, customerId, customerId, customerId, customerId)));
        result.put("recentPayments", rows("SELECT id, amount, status, reconciliation_state, recharge_id, booking_id, subscription_id, created_at "
                + "FROM payment_record WHERE customer_id = ? ORDER BY created_at DESC, id DESC LIMIT 10", customerId));
        result.put("subscriptions", rows("SELECT s.id, p.name AS plan, s.status, s.starts_at, s.expires_at, s.auto_renew FROM customer_subscription s "
                + "JOIN subscription_plan p ON p.id = s.plan_id WHERE s.customer_id = ? ORDER BY s.id DESC LIMIT 10", customerId));
        result.put("recentTickets", rows("SELECT id, ticket_code, subject, priority, status, created_at FROM support_ticket "
                + "WHERE customer_id = ? ORDER BY created_at DESC LIMIT 10", customerId));
        result.put("activity", rows("SELECT id, activity_type, summary, created_at FROM customer_activity "
                + "WHERE customer_id = ? ORDER BY created_at DESC, id DESC LIMIT 15", customerId));
        return result;
    }

    @Transactional(readOnly = true)
    public Page<Map<String, Object>> payments(String status, String reconciliation, String q, Long customerId, Pageable pageable) {
        StringBuilder where = new StringBuilder(" WHERE 1 = 1");
        List<Object> args = new ArrayList<>();
        if (status != null && !status.isBlank()) {
            where.append(" AND p.status = ?");
            args.add(status);
        }
        if (reconciliation != null && !reconciliation.isBlank()) {
            where.append(" AND p.reconciliation_state = ?");
            args.add(reconciliation);
        }
        if (customerId != null) {
            where.append(" AND p.customer_id = ?");
            args.add(customerId);
        }
        if (q != null && !q.isBlank()) {
            if (q.strip().matches("\\d{1,18}")) {
                where.append(" AND p.id = ?");
                args.add(Long.parseLong(q.strip()));
            } else {
                where.append(" AND p.provider_reference LIKE ?");
                args.add(q.strip() + "%");
            }
        }
        return page("SELECT p.id, p.customer_id, p.amount, p.discount_amount, p.currency, p.status, p.reconciliation_state, "
                + "p.failure_reason, p.provider_reference, p.recharge_id, p.booking_id, p.subscription_id, p.attempt_count, p.created_at "
                + "FROM payment_record p" + where + " ORDER BY p.created_at DESC, p.id DESC LIMIT ? OFFSET ?",
                "SELECT COUNT(*) FROM payment_record p" + where, args, pageable);
    }

    @Transactional(readOnly = true)
    public Page<Map<String, Object>> refunds(String status, Pageable pageable) {
        StringBuilder where = new StringBuilder(" WHERE 1 = 1");
        List<Object> args = new ArrayList<>();
        if (status != null && !status.isBlank()) {
            where.append(" AND r.status = ?");
            args.add(status);
        }
        return page("SELECT r.id, r.payment_id, p.customer_id, r.amount, p.amount AS payment_amount, r.status, r.reason, r.created_at "
                + "FROM refund_record r JOIN payment_record p ON p.id = r.payment_id" + where
                + " ORDER BY r.created_at DESC, r.id DESC LIMIT ? OFFSET ?",
                "SELECT COUNT(*) FROM refund_record r" + where, args, pageable);
    }

    /**
     * Resolves a payment stuck in PROCESSING / flagged for reconciliation by applying the outcome the provider
     * reported. Recharge and booking payments are supported; each state change is guarded so a double click or a
     * concurrent callback cannot apply it twice.
     */
    @Transactional
    public Map<String, Object> reconcile(long actor, long paymentId, String outcome, String note) {
        Map<String, Object> payment;
        try {
            payment = jdbcTemplate.queryForMap(
                    "SELECT id, customer_id, status, reconciliation_state, recharge_id, booking_id, subscription_id "
                            + "FROM payment_record WHERE id = ? FOR UPDATE", paymentId);
        } catch (EmptyResultDataAccessException exception) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Payment was not found");
        }
        String state = (String) payment.get("reconciliation_state");
        if ("NONE".equals(state) || "RECONCILED".equals(state)) {
            throw new ApiException(HttpStatus.CONFLICT, "Payment does not require reconciliation");
        }
        if (payment.get("subscription_id") != null) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "Subscription payments cannot be reconciled manually");
        }
        boolean succeeded = "SUCCEEDED".equals(outcome);
        jdbcTemplate.update(
                "UPDATE payment_record SET status = ?, reconciliation_state = 'RECONCILED', "
                        + "provider_reference = COALESCE(provider_reference, ?), failure_reason = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?",
                succeeded ? "SUCCEEDED" : "FAILED", "SIM-RECON-" + paymentId, succeeded ? null : "RECONCILED_AS_FAILED", paymentId);
        if (payment.get("recharge_id") != null) {
            long rechargeId = ((Number) payment.get("recharge_id")).longValue();
            if (succeeded) {
                jdbcTemplate.update(
                        "UPDATE recharge_transaction r SET status = 'SUCCEEDED', updated_at = CURRENT_TIMESTAMP, "
                                + "expires_at = CURRENT_TIMESTAMP + (SELECT validity_days FROM recharge_plan p WHERE p.id = r.plan_id) * INTERVAL '1 day' "
                                + "WHERE id = ? AND status IN ('PENDING', 'PROCESSING')", rechargeId);
            } else {
                jdbcTemplate.update(
                        "UPDATE recharge_transaction SET status = 'FAILED', updated_at = CURRENT_TIMESTAMP "
                                + "WHERE id = ? AND status IN ('PENDING', 'PROCESSING')", rechargeId);
                coupons.release(paymentId);
            }
        }
        if (payment.get("booking_id") != null) {
            long bookingId = ((Number) payment.get("booking_id")).longValue();
            if (succeeded) {
                jdbcTemplate.update("UPDATE booking_record SET status = 'CONFIRMED', updated_at = CURRENT_TIMESTAMP, version = version + 1 "
                        + "WHERE id = ? AND status = 'PENDING'", bookingId);
            } else {
                jdbcTemplate.update("UPDATE booking_record SET status = 'CANCELLED', updated_at = CURRENT_TIMESTAMP, version = version + 1 "
                        + "WHERE id = ? AND status = 'PENDING'", bookingId);
                jdbcTemplate.update("DELETE FROM booking_seat_claim WHERE booking_id = ?", bookingId);
                coupons.release(paymentId);
            }
        }
        long customerId = ((Number) payment.get("customer_id")).longValue();
        notifications.enqueue(customerId, succeeded ? "PAYMENT_SUCCEEDED" : "PAYMENT_FAILED",
                succeeded ? "Payment confirmed" : "Payment failed", "Payment " + paymentId + " was resolved after review.",
                "payment-reconciled-" + paymentId);
        audit.record(actor, "PAYMENT_RECONCILED", "PAYMENT", paymentId, outcome + (note == null ? "" : ": " + note));
        metrics.count("payment.reconciled", "outcome", outcome);
        return JdbcRowSupport.camelCaseRow(jdbcTemplate.queryForMap(
                "SELECT id, status, reconciliation_state, provider_reference FROM payment_record WHERE id = ?", paymentId));
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> permissions() {
        return rows("SELECT id, permission_code, description FROM app_permission ORDER BY id");
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> streamingContent() {
        return rows("SELECT c.id, c.content_code, c.title, c.description, c.content_type, c.rating, c.duration_minutes, c.active, "
                + "g.id AS category_id, g.name AS category FROM streaming_content c JOIN streaming_category g ON g.id = c.category_id "
                + "ORDER BY c.id DESC LIMIT 500");
    }

    @Transactional
    public void updateStreamingContent(long actor, long id, String title, String description, boolean active) {
        int updated = jdbcTemplate.update(
                "UPDATE streaming_content SET title = ?, description = ?, active = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?",
                title, description, active, id);
        if (updated != 1) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Streaming content was not found");
        }
        audit.record(actor, "STREAMING_CONTENT_UPDATED", "STREAMING_CONTENT", id);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> bookingItems() {
        return rows("SELECT i.id, i.item_type, i.item_code, i.title, i.location, i.starts_at, i.capacity, i.price, i.available, "
                + "(SELECT COUNT(*) FROM booking_seat_claim s WHERE s.catalog_item_id = i.id) AS claimed "
                + "FROM booking_catalog_item i ORDER BY i.starts_at DESC LIMIT 300");
    }

    @Transactional
    public long createBookingItem(long actor, String type, String code, String title, String location,
            LocalDateTime startsAt, int capacity, java.math.BigDecimal price) {
        long id = GeneratedKeySupport.insert(jdbcTemplate,
                "INSERT INTO booking_catalog_item (item_type, item_code, title, location, starts_at, capacity, price, category) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                type, code, title, location, Timestamp.valueOf(startsAt), capacity, price, type);
        audit.record(actor, "BOOKING_ITEM_CREATED", "BOOKING_ITEM", id);
        return id;
    }

    @Transactional
    public void updateBookingItem(long actor, long id, String title, java.math.BigDecimal price, boolean available) {
        int updated = jdbcTemplate.update(
                "UPDATE booking_catalog_item SET title = ?, price = ?, available = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?",
                title, price, available, id);
        if (updated != 1) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Booking catalogue item was not found");
        }
        audit.record(actor, "BOOKING_ITEM_UPDATED", "BOOKING_ITEM", id);
    }

    private List<Map<String, Object>> rows(String sql, Object... args) {
        return JdbcRowSupport.camelCaseRows(jdbcTemplate.queryForList(sql, args));
    }

    private Page<Map<String, Object>> page(String sql, String countSql, List<Object> filterArgs, Pageable pageable) {
        int size = Math.min(Math.max(pageable.getPageSize(), 1), 100);
        long offset = (long) Math.max(pageable.getPageNumber(), 0) * size;
        List<Object> args = new ArrayList<>(filterArgs);
        args.add(size);
        args.add(offset);
        List<Map<String, Object>> content = JdbcRowSupport.camelCaseRows(jdbcTemplate.queryForList(sql, args.toArray()));
        Long total = jdbcTemplate.queryForObject(countSql, Long.class, filterArgs.toArray());
        return new PageImpl<>(content, org.springframework.data.domain.PageRequest.of(Math.max(pageable.getPageNumber(), 0), size),
                total == null ? 0 : total);
    }
}
