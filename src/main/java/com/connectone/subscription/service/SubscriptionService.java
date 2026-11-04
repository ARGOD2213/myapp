package com.connectone.subscription.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.connectone.admin.service.CheckoutCouponService;
import com.connectone.common.exception.ApiException;
import com.connectone.notification.service.NotificationService;
import com.connectone.subscription.dto.SubscriptionDtos.EntitlementResponse;
import com.connectone.subscription.dto.SubscriptionDtos.PlanResponse;
import com.connectone.subscription.dto.SubscriptionDtos.PurchaseRequest;
import com.connectone.subscription.dto.SubscriptionDtos.SubscriptionResponse;

@Service
public class SubscriptionService {

    private final JdbcTemplate jdbcTemplate;
    private final NotificationService notifications;
    private final CheckoutCouponService coupons;

    public SubscriptionService(JdbcTemplate jdbcTemplate, NotificationService notifications, CheckoutCouponService coupons) {
        this.jdbcTemplate = jdbcTemplate;
        this.coupons = coupons;
        this.notifications = notifications;
    }

    @Transactional(readOnly = true)
    public List<PlanResponse> plans() {
        return jdbcTemplate.query(
                "SELECT id, plan_code, name, price, billing_period_days FROM subscription_plan "
                        + "WHERE active = TRUE ORDER BY price, id",
                (result, row) -> new PlanResponse(
                        result.getLong("id"),
                        result.getString("plan_code"),
                        result.getString("name"),
                        result.getBigDecimal("price"),
                        result.getInt("billing_period_days")));
    }

    @Transactional
    public SubscriptionResponse purchase(long userId, PurchaseRequest request) {
        long customerId = activeCustomerId(userId);
        jdbcTemplate.queryForObject(
                "SELECT id FROM customer_profile WHERE id = ? FOR UPDATE", Long.class, customerId);
        Map<String, Object> plan = queryForMap(
                "SELECT id, plan_code, name, price, billing_period_days FROM subscription_plan "
                        + "WHERE id = ? AND active = TRUE",
                request.planId());
        String paymentKey = "subscription-" + sha256(request.idempotencyKey());
        Map<String, Object> prior = findPaymentByIdempotencyKey(paymentKey);
        if (prior != null) {
            if (((Number) prior.get("customer_id")).longValue() != customerId) {
                throw new ApiException(HttpStatus.CONFLICT, "Idempotency key was already used");
            }
            return getSubscription(
                    ((Number) prior.get("subscription_id")).longValue(),
                    ((Number) prior.get("id")).longValue(),
                    customerId);
        }
        Integer existing = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM customer_subscription WHERE customer_id = ? AND plan_id = ? "
                        + "AND status IN ('PENDING', 'ACTIVE')",
                Integer.class, customerId, request.planId());
        if (existing != null && existing > 0) {
            throw new ApiException(HttpStatus.CONFLICT, "An active or pending subscription already exists for this plan");
        }
        long subscriptionId = insertSubscription(
                customerId,
                request.planId(),
                request.autoRenew(),
                ((Number) plan.get("billing_period_days")).intValue());
        long paymentId = insertPayment(
                customerId,
                subscriptionId,
                (java.math.BigDecimal) plan.get("price"),
                paymentKey);
        coupons.applyToPayment(customerId, request.couponCode(), paymentKey, (java.math.BigDecimal) plan.get("price"));
        event(subscriptionId, "PURCHASE_INITIATED", "Subscription purchase payment initiated");
        return getSubscription(subscriptionId, paymentId, customerId);
    }

    @Transactional
    public SubscriptionResponse renew(long userId, long subscriptionId, String idempotencyKey) {
        long customerId = activeCustomerId(userId);
        Map<String, Object> subscription = queryForMap(
                "SELECT s.id, s.plan_id, s.status FROM customer_subscription s "
                        + "WHERE s.id = ? AND s.customer_id = ? FOR UPDATE",
                subscriptionId, customerId);
        String paymentKey = "renewal-" + sha256(idempotencyKey);
        Map<String, Object> prior = findPaymentByIdempotencyKey(paymentKey);
        if (prior != null) {
            if (((Number) prior.get("customer_id")).longValue() != customerId
                    || ((Number) prior.get("subscription_id")).longValue() != subscriptionId) {
                throw new ApiException(HttpStatus.CONFLICT, "Idempotency key was already used");
            }
            return getSubscription(subscriptionId, ((Number) prior.get("id")).longValue(), customerId);
        }
        String status = (String) subscription.get("status");
        if (!"ACTIVE".equals(status) && !"RENEWAL_FAILED".equals(status) && !"EXPIRED".equals(status)) {
            throw new ApiException(HttpStatus.CONFLICT, "Subscription cannot be renewed in its current state");
        }
        Integer pendingPayments = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM payment_record WHERE subscription_id = ? AND status = 'INITIATED'",
                Integer.class, subscriptionId);
        if (pendingPayments != null && pendingPayments > 0) {
            throw new ApiException(HttpStatus.CONFLICT, "A subscription payment is already pending");
        }
        Map<String, Object> plan = queryForMap(
                "SELECT price FROM subscription_plan WHERE id = ? AND active = TRUE",
                ((Number) subscription.get("plan_id")).longValue());
        if ("EXPIRED".equals(status)) {
            jdbcTemplate.update(
                "UPDATE customer_subscription SET status = 'ACTIVE', version = version + 1 WHERE id = ? "
                        + "AND status = 'EXPIRED'",
                subscriptionId);
        }
        long paymentId = insertPayment(
                customerId,
                subscriptionId,
                (java.math.BigDecimal) plan.get("price"),
                paymentKey);
        event(subscriptionId, "RENEWAL_INITIATED", "Subscription renewal payment initiated");
        return getSubscription(subscriptionId, paymentId, customerId);
    }

    @Transactional
    public SubscriptionResponse cancel(long userId, long subscriptionId) {
        long customerId = activeCustomerId(userId);
        Map<String, Object> subscription = queryForMap(
                "SELECT status FROM customer_subscription WHERE id = ? AND customer_id = ? FOR UPDATE",
                subscriptionId, customerId);
        String status = (String) subscription.get("status");
        if (!"ACTIVE".equals(status) && !"PENDING".equals(status) && !"RENEWAL_FAILED".equals(status)) {
            throw new ApiException(HttpStatus.CONFLICT, "Subscription cannot be cancelled in its current state");
        }
        jdbcTemplate.update(
                "UPDATE customer_subscription SET status = 'CANCELLED', auto_renew = FALSE, version = version + 1 "
                        + "WHERE id = ?",
                subscriptionId);
        event(subscriptionId, "CANCELLED", "Subscription cancelled by customer");
        return currentSubscription(subscriptionId, customerId);
    }

    @Transactional(readOnly = true)
    public EntitlementResponse entitlement(long userId, long subscriptionId) {
        long customerId = activeCustomerId(userId);
        Map<String, Object> subscription = queryForMap(
                "SELECT s.id, s.status, s.expires_at, p.plan_code, "
                        + "EXISTS (SELECT 1 FROM payment_record pay WHERE pay.subscription_id = s.id "
                        + "AND pay.status IN ('SUCCEEDED', 'REFUNDED')) AS has_successful_payment "
                        + "FROM customer_subscription s "
                        + "JOIN subscription_plan p ON p.id = s.plan_id "
                        + "WHERE s.id = ? AND s.customer_id = ?",
                subscriptionId, customerId);
        LocalDateTime expiresAt = ((Timestamp) subscription.get("expires_at")).toLocalDateTime();
        String status = (String) subscription.get("status");
        boolean entitled = Boolean.TRUE.equals(subscription.get("has_successful_payment"))
                && ("ACTIVE".equals(status) || "RENEWAL_FAILED".equals(status))
                && expiresAt.isAfter(LocalDateTime.now());
        return new EntitlementResponse(subscriptionId, (String) subscription.get("plan_code"), entitled, expiresAt);
    }

    @Transactional(readOnly = true)
    public List<SubscriptionResponse> history(long userId) {
        long customerId = activeCustomerId(userId);
        return jdbcTemplate.query(
                "SELECT s.id, p.plan_code, p.name, s.status, s.starts_at, s.expires_at, s.auto_renew, "
                        + "(SELECT MAX(pay.id) FROM payment_record pay WHERE pay.subscription_id = s.id) AS payment_id "
                        + "FROM customer_subscription s JOIN subscription_plan p ON p.id = s.plan_id "
                        + "WHERE s.customer_id = ? ORDER BY s.created_at DESC, s.id DESC",
                (result, row) -> new SubscriptionResponse(
                        result.getLong("id"),
                        result.getLong("payment_id"),
                        result.getString("plan_code"),
                        result.getString("name"),
                        result.getString("status"),
                        result.getTimestamp("starts_at").toLocalDateTime(),
                        result.getTimestamp("expires_at").toLocalDateTime(),
                        result.getBoolean("auto_renew")),
                customerId);
    }

    @Transactional
    public int expireDueSubscriptions(int batchSize) {
        List<Long> ids = jdbcTemplate.query(
                "SELECT id FROM customer_subscription WHERE status IN ('ACTIVE', 'RENEWAL_FAILED') "
                        + "AND expires_at <= CURRENT_TIMESTAMP "
                        + "ORDER BY expires_at LIMIT ? FOR UPDATE",
                (result, row) -> result.getLong(1),
                Math.min(Math.max(batchSize, 1), 500));
        for (Long id : ids) {
            int updated = jdbcTemplate.update(
                    "UPDATE customer_subscription SET status = 'EXPIRED', version = version + 1 "
                            + "WHERE id = ? AND status IN ('ACTIVE', 'RENEWAL_FAILED') "
                            + "AND expires_at <= CURRENT_TIMESTAMP",
                    id);
            if (updated == 1) {
                event(id, "EXPIRED", "Subscription validity period ended");
            }
        }
        return ids.size();
    }

    @Transactional
    public int remindExpiringSubscriptions(int batchSize) {
        int bounded = Math.min(Math.max(batchSize, 1), 500);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT s.id, s.customer_id, s.expires_at FROM customer_subscription s "
                        + "WHERE s.status = 'ACTIVE' AND s.expires_at > CURRENT_TIMESTAMP "
                        + "AND s.expires_at <= CURRENT_TIMESTAMP + INTERVAL '3' DAY "
                        + "ORDER BY s.expires_at LIMIT ?",
                bounded);
        for (Map<String, Object> row : rows) {
            long subscriptionId = ((Number) row.get("id")).longValue();
            java.sql.Timestamp expiry = (java.sql.Timestamp) row.get("expires_at");
            notifications.enqueue(((Number) row.get("customer_id")).longValue(), "SUBSCRIPTION_EXPIRING",
                    "Subscription expiry reminder",
                    "Your subscription expires at " + expiry.toLocalDateTime() + ".",
                    "subscription-expiry-" + subscriptionId + "-" + expiry.toLocalDateTime().toLocalDate());
        }
        return rows.size();
    }

    private long insertSubscription(long customerId, long planId, boolean autoRenew, int billingPeriodDays) {
        KeyHolder holder = new GeneratedKeyHolder();
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO customer_subscription "
                            + "(customer_id, plan_id, status, starts_at, expires_at, auto_renew) "
                            + "VALUES (?, ?, 'PENDING', ?, ?, ?)",
                    new String[] {"id"});
            statement.setLong(1, customerId);
            statement.setLong(2, planId);
            statement.setTimestamp(3, Timestamp.valueOf(now));
            statement.setTimestamp(4, Timestamp.valueOf(now.plusDays(billingPeriodDays)));
            statement.setBoolean(5, autoRenew);
            return statement;
        }, holder);
        Number id = holder.getKey();
        if (id == null) {
            throw new IllegalStateException("Database did not return the subscription identifier");
        }
        return id.longValue();
    }

    private long insertPayment(
            long customerId,
            long subscriptionId,
            java.math.BigDecimal amount,
            String idempotencyKey) {
        KeyHolder holder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO payment_record "
                            + "(customer_id, subscription_id, amount, currency, status, idempotency_key) "
                            + "VALUES (?, ?, ?, 'INR', 'INITIATED', ?)",
                    new String[] {"id"});
            statement.setLong(1, customerId);
            statement.setLong(2, subscriptionId);
            statement.setBigDecimal(3, amount);
            statement.setString(4, idempotencyKey);
            return statement;
        }, holder);
        Number id = holder.getKey();
        if (id == null) {
            throw new IllegalStateException("Database did not return the payment identifier");
        }
        return id.longValue();
    }

    private SubscriptionResponse currentSubscription(long subscriptionId, long customerId) {
        Map<String, Object> row = queryForMap(
                "SELECT s.id, p.plan_code, p.name, s.status, s.starts_at, s.expires_at, s.auto_renew, "
                        + "(SELECT MAX(pay.id) FROM payment_record pay WHERE pay.subscription_id = s.id) AS payment_id "
                        + "FROM customer_subscription s JOIN subscription_plan p ON p.id = s.plan_id "
                        + "WHERE s.id = ? AND s.customer_id = ?",
                subscriptionId, customerId);
        return response(row);
    }

    private SubscriptionResponse getSubscription(long subscriptionId, long paymentId, long customerId) {
        Map<String, Object> row = queryForMap(
                "SELECT s.id, p.plan_code, p.name, s.status, s.starts_at, s.expires_at, s.auto_renew "
                        + "FROM customer_subscription s JOIN subscription_plan p ON p.id = s.plan_id "
                        + "WHERE s.id = ? AND s.customer_id = ?",
                subscriptionId, customerId);
        row.put("payment_id", paymentId);
        return response(row);
    }

    private SubscriptionResponse response(Map<String, Object> row) {
        return new SubscriptionResponse(
                ((Number) row.get("id")).longValue(),
                ((Number) row.get("payment_id")).longValue(),
                (String) row.get("plan_code"),
                (String) row.get("name"),
                (String) row.get("status"),
                ((Timestamp) row.get("starts_at")).toLocalDateTime(),
                ((Timestamp) row.get("expires_at")).toLocalDateTime(),
                (Boolean) row.get("auto_renew"));
    }

    private Map<String, Object> findPaymentByIdempotencyKey(String key) {
        try {
            return jdbcTemplate.queryForMap(
                    "SELECT id, customer_id, subscription_id FROM payment_record WHERE idempotency_key = ?",
                    key);
        } catch (EmptyResultDataAccessException exception) {
            return null;
        }
    }

    private Map<String, Object> queryForMap(String sql, Object... args) {
        try {
            return jdbcTemplate.queryForMap(sql, args);
        } catch (EmptyResultDataAccessException exception) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Requested resource was not found");
        }
    }

    private long activeCustomerId(long userId) {
        try {
            Map<String, Object> customer = jdbcTemplate.queryForMap(
                    "SELECT id, account_status FROM customer_profile WHERE user_id = ?", userId);
            if (!"ACTIVE".equals(customer.get("account_status"))) {
                throw new ApiException(HttpStatus.FORBIDDEN, "Customer account is not active");
            }
            return ((Number) customer.get("id")).longValue();
        } catch (EmptyResultDataAccessException exception) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Customer profile was not found");
        }
    }

    private void event(long subscriptionId, String type, String details) {
        jdbcTemplate.update(
                "INSERT INTO subscription_event (subscription_id, event_type, details) VALUES (?, ?, ?)",
                subscriptionId, type, details);
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to hash an idempotency key", exception);
        }
    }
}
