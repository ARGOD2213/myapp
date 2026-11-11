package com.connectone.payment.service;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
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
import com.connectone.common.metrics.BusinessMetrics;
import com.connectone.notification.service.NotificationService;
import com.connectone.payment.dto.PaymentDtos.ConfirmPaymentRequest;
import com.connectone.payment.dto.PaymentDtos.PaymentResponse;
import com.connectone.payment.dto.PaymentDtos.RefundRequest;
import com.connectone.payment.dto.PaymentDtos.RefundResponse;

@Service
public class PaymentService {

    private final JdbcTemplate jdbcTemplate;
    private final PaymentProvider paymentProvider;
    private final NotificationService notifications;
    private final CheckoutCouponService coupons;
    private final BusinessMetrics metrics;

    public PaymentService(
            JdbcTemplate jdbcTemplate,
            PaymentProvider paymentProvider,
            NotificationService notifications,
            CheckoutCouponService coupons,
            BusinessMetrics metrics) {
        this.jdbcTemplate = jdbcTemplate;
        this.coupons = coupons;
        this.metrics = metrics;
        this.paymentProvider = paymentProvider;
        this.notifications = notifications;
    }

    @Transactional(readOnly = true)
    public List<PaymentResponse> history(long userId) {
        long customerId = customerId(userId);
        return jdbcTemplate.query(
                "SELECT id, amount, currency, status, provider_reference, created_at "
                        + "FROM payment_record WHERE customer_id = ? ORDER BY created_at DESC, id DESC",
                (result, row) -> toPaymentResponse(
                        result.getLong("id"),
                        result.getBigDecimal("amount"),
                        result.getString("currency"),
                        result.getString("status"),
                        result.getString("provider_reference"),
                        result.getTimestamp("created_at")),
                customerId);
    }

    @Transactional(readOnly = true)
    public List<PaymentResponse> monitor(String status) {
        if (status == null || status.isBlank()) {
            return jdbcTemplate.query(
                    "SELECT id, amount, currency, status, provider_reference, created_at "
                            + "FROM payment_record ORDER BY created_at DESC, id DESC LIMIT 200",
                    (result, row) -> toPaymentResponse(
                            result.getLong("id"),
                            result.getBigDecimal("amount"),
                            result.getString("currency"),
                            result.getString("status"),
                            result.getString("provider_reference"),
                            result.getTimestamp("created_at")));
        }
        return jdbcTemplate.query(
                "SELECT id, amount, currency, status, provider_reference, created_at "
                        + "FROM payment_record WHERE status = ? ORDER BY created_at DESC, id DESC LIMIT 200",
                (result, row) -> toPaymentResponse(
                        result.getLong("id"),
                        result.getBigDecimal("amount"),
                        result.getString("currency"),
                        result.getString("status"),
                        result.getString("provider_reference"),
                        result.getTimestamp("created_at")),
                status);
    }

    @Transactional
    public PaymentResponse confirm(long userId, long paymentId, ConfirmPaymentRequest request) {
        Map<String, Object> payment = findPayment(paymentId, true);
        if (((Number) payment.get("customer_id")).longValue() != customerId(userId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Payment was not found");
        }
        Map<String, Object> existingCallback = findCallback(request.callbackId());
        if (existingCallback != null) {
            if (((Number) existingCallback.get("payment_id")).longValue() != paymentId
                    || !request.outcome().equals(existingCallback.get("outcome"))) {
                throw new ApiException(HttpStatus.CONFLICT, "Callback identifier was already used");
            }
            return getPayment(paymentId);
        }
        if (!"INITIATED".equals(payment.get("status"))) {
            throw new ApiException(HttpStatus.CONFLICT, "Payment is not awaiting confirmation");
        }

        PaymentProvider.ProviderResult result =
                paymentProvider.confirm(request.outcome(), request.callbackId());
        jdbcTemplate.update(
                "INSERT INTO payment_callback (callback_id, payment_id, outcome) VALUES (?, ?, ?)",
                request.callbackId(), paymentId, result.outcome());
        String paymentStatus = "SUCCEEDED".equals(result.outcome()) ? "SUCCEEDED" : "FAILED";
        jdbcTemplate.update(
                "UPDATE payment_record SET status = ?, provider_reference = ?, updated_at = CURRENT_TIMESTAMP "
                        + "WHERE id = ? AND status = 'INITIATED'",
                paymentStatus, result.reference(), paymentId);
        updateLinkedBusinessStatus(payment, result.outcome());
        if (!succeeded(result.outcome())) {
            coupons.release(paymentId);
        }
        metrics.count("payment.confirmed", "outcome", result.outcome());
        notifyPaymentOutcome(payment, paymentId, succeeded(result.outcome()));
        return getPayment(paymentId);
    }

    @Transactional
    public RefundResponse refund(long userId, long paymentId, RefundRequest request) {
        Map<String, Object> payment = findPayment(paymentId, true);
        if (((Number) payment.get("customer_id")).longValue() != customerId(userId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Payment was not found");
        }
        Map<String, Object> existing = findRefund(request.idempotencyKey());
        if (existing != null) {
            if (((Number) existing.get("payment_id")).longValue() != paymentId
                    || ((BigDecimal) existing.get("amount")).compareTo(request.amount()) != 0) {
                throw new ApiException(HttpStatus.CONFLICT, "Idempotency key was already used for a different refund");
            }
            return toRefundResponse(existing);
        }
        if (!"SUCCEEDED".equals(payment.get("status"))) {
            throw new ApiException(HttpStatus.CONFLICT, "Only successful payments can be refunded");
        }
        BigDecimal refunded = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(amount), 0) FROM refund_record "
                        + "WHERE payment_id = ? AND status IN ('PENDING', 'SUCCEEDED')",
                BigDecimal.class,
                paymentId);
        BigDecimal totalRefund = refunded.add(request.amount());
        BigDecimal paidAmount = (BigDecimal) payment.get("amount");
        if (totalRefund.compareTo(paidAmount) > 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Refund amount exceeds the remaining payment balance");
        }

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO refund_record (payment_id, amount, status, reason, idempotency_key) "
                            + "VALUES (?, ?, 'PENDING', ?, ?)",
                    new String[] {"id"});
            statement.setLong(1, paymentId);
            statement.setBigDecimal(2, request.amount());
            statement.setString(3, request.reason().strip());
            statement.setString(4, request.idempotencyKey());
            return statement;
        }, keyHolder);
        Number refundId = keyHolder.getKey();
        if (refundId == null) {
            throw new IllegalStateException("Database did not return the refund identifier");
        }

        jdbcTemplate.update("UPDATE refund_record SET status = 'SUCCEEDED' WHERE id = ?", refundId.longValue());
        if (totalRefund.compareTo(paidAmount) == 0) {
            jdbcTemplate.update(
                    "UPDATE payment_record SET status = 'REFUNDED', updated_at = CURRENT_TIMESTAMP WHERE id = ?",
                    paymentId);
            if (payment.get("recharge_id") != null) {
                jdbcTemplate.update(
                        "UPDATE recharge_transaction SET status = 'REVERSED', updated_at = CURRENT_TIMESTAMP "
                                + "WHERE id = ? AND status = 'SUCCEEDED'",
                        ((Number) payment.get("recharge_id")).longValue());
            }
            if (payment.get("subscription_id") != null) {
                long subscriptionId = ((Number) payment.get("subscription_id")).longValue();
                int cancelled = jdbcTemplate.update(
                        "UPDATE customer_subscription SET status = 'CANCELLED', auto_renew = FALSE, "
                                + "version = version + 1 WHERE id = ? "
                                + "AND status IN ('PENDING', 'ACTIVE', 'RENEWAL_FAILED')",
                        subscriptionId);
                if (cancelled == 1) {
                    jdbcTemplate.update(
                            "INSERT INTO subscription_event (subscription_id, event_type, details) "
                                    + "VALUES (?, 'REFUNDED', 'Subscription payment was fully refunded')",
                            subscriptionId);
                }
            }
            if (payment.get("booking_id") != null) {
                long bookingId = ((Number) payment.get("booking_id")).longValue();
                int cancelled = jdbcTemplate.update(
                        "UPDATE booking_record SET status = 'REFUNDED', updated_at = CURRENT_TIMESTAMP, "
                                + "version = version + 1 WHERE id = ? AND status = 'CONFIRMED'",
                        bookingId);
                if (cancelled != 1) {
                    throw new ApiException(HttpStatus.CONFLICT, "Booking is not in a refundable state");
                }
                jdbcTemplate.update("DELETE FROM booking_seat_claim WHERE booking_id = ?", bookingId);
            }
        }
        metrics.count("refund.created");
        notifications.enqueue(((Number) payment.get("customer_id")).longValue(), "REFUND_SUCCEEDED",
                "Refund processed", "A refund of " + request.amount() + " was processed.",
                "refund-" + request.idempotencyKey());
        return jdbcTemplate.queryForObject(
                "SELECT id, payment_id, amount, status, reason, created_at FROM refund_record WHERE id = ?",
                (result, row) -> new RefundResponse(
                        result.getLong("id"),
                        result.getLong("payment_id"),
                        result.getBigDecimal("amount"),
                        result.getString("status"),
                        result.getString("reason"),
                        toLocalDateTime(result.getTimestamp("created_at"))),
                refundId.longValue());
    }

    private void updateLinkedBusinessStatus(Map<String, Object> payment, String outcome) {
        boolean succeeded = succeeded(outcome);
        Object rechargeId = payment.get("recharge_id");
        if (rechargeId != null) {
            if (succeeded) {
                Integer validityDays = jdbcTemplate.queryForObject(
                        "SELECT p.validity_days FROM recharge_transaction r "
                                + "JOIN recharge_plan p ON p.id = r.plan_id WHERE r.id = ?",
                        Integer.class, ((Number) rechargeId).longValue());
                int updated = jdbcTemplate.update(
                        "UPDATE recharge_transaction SET status = 'SUCCEEDED', "
                                + "expires_at = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ? AND status = 'PENDING'",
                        Timestamp.valueOf(LocalDateTime.now().plusDays(validityDays)),
                        ((Number) rechargeId).longValue());
                if (updated != 1) {
                    throw new ApiException(HttpStatus.CONFLICT, "Recharge is not awaiting payment confirmation");
                }
            } else {
                int updated = jdbcTemplate.update(
                        "UPDATE recharge_transaction SET status = 'FAILED', updated_at = CURRENT_TIMESTAMP "
                                + "WHERE id = ? AND status = 'PENDING'",
                        ((Number) rechargeId).longValue());
                if (updated != 1) {
                    throw new ApiException(HttpStatus.CONFLICT, "Recharge is not awaiting payment confirmation");
                }
            }
        }
        Object subscriptionId = payment.get("subscription_id");
        if (subscriptionId != null) {
            long id = ((Number) subscriptionId).longValue();
            if (succeeded) {
                Integer days = jdbcTemplate.queryForObject(
                        "SELECT p.billing_period_days FROM customer_subscription s "
                                + "JOIN subscription_plan p ON p.id = s.plan_id WHERE s.id = ?",
                        Integer.class, id);
                Map<String, Object> subscription = jdbcTemplate.queryForMap(
                        "SELECT status, expires_at FROM customer_subscription WHERE id = ? FOR UPDATE", id);
                LocalDateTime now = LocalDateTime.now();
                LocalDateTime expiration = ((Timestamp) subscription.get("expires_at")).toLocalDateTime();
                LocalDateTime newExpiration = "PENDING".equals(subscription.get("status")) || !expiration.isAfter(now)
                        ? now.plusDays(days)
                        : expiration.plusDays(days);
                int updated = jdbcTemplate.update(
                        "UPDATE customer_subscription SET "
                                + "starts_at = CASE WHEN status = 'PENDING' THEN ? ELSE starts_at END, "
                                + "expires_at = ?, status = 'ACTIVE', version = version + 1 "
                                + "WHERE id = ? AND status IN ('PENDING', 'ACTIVE', 'RENEWAL_FAILED')",
                        Timestamp.valueOf(now),
                        Timestamp.valueOf(newExpiration),
                        id);
                if (updated != 1) {
                    throw new ApiException(HttpStatus.CONFLICT, "Subscription is not awaiting payment confirmation");
                }
            } else {
                int updated = jdbcTemplate.update(
                        "UPDATE customer_subscription SET status = 'RENEWAL_FAILED', version = version + 1 "
                                + "WHERE id = ? AND status IN ('PENDING', 'ACTIVE')",
                        id);
                if (updated != 1) {
                    throw new ApiException(HttpStatus.CONFLICT, "Subscription is not awaiting payment confirmation");
                }
            }
        }
        Object bookingId = payment.get("booking_id");
        if (bookingId != null) {
            long id = ((Number) bookingId).longValue();
            if (succeeded) {
                int updated = jdbcTemplate.update(
                        "UPDATE booking_record SET status = 'CONFIRMED', updated_at = CURRENT_TIMESTAMP, "
                                + "version = version + 1 WHERE id = ? AND status = 'PENDING'",
                        id);
                if (updated != 1) {
                    throw new ApiException(HttpStatus.CONFLICT, "Booking is not awaiting payment confirmation");
                }
            } else {
                int updated = jdbcTemplate.update(
                        "UPDATE booking_record SET status = 'CANCELLED', updated_at = CURRENT_TIMESTAMP, "
                                + "version = version + 1 WHERE id = ? AND status = 'PENDING'",
                        id);
                if (updated != 1) {
                    throw new ApiException(HttpStatus.CONFLICT, "Booking is not awaiting payment confirmation");
                }
                jdbcTemplate.update("DELETE FROM booking_seat_claim WHERE booking_id = ?", id);
            }
        }
    }

    private void notifyPaymentOutcome(Map<String, Object> payment, long paymentId, boolean succeeded) {
            long customerId = ((Number) payment.get("customer_id")).longValue();
            String outcome = succeeded ? "SUCCEEDED" : "FAILED";
            notifications.enqueue(customerId, succeeded ? "PAYMENT_SUCCEEDED" : "PAYMENT_FAILED",
                    succeeded ? "Payment successful" : "Payment failed",
                    "Payment " + paymentId + " " + outcome.toLowerCase(java.util.Locale.ROOT) + ".",
                    "payment-" + paymentId + "-" + outcome);
            if (payment.get("booking_id") != null) {
                notifications.enqueue(customerId, succeeded ? "BOOKING_CONFIRMED" : "BOOKING_PAYMENT_FAILED",
                        succeeded ? "Booking confirmed" : "Booking payment failed",
                        succeeded ? "Your booking is confirmed." : "Your booking could not be completed.",
                        "booking-payment-" + payment.get("booking_id") + "-" + outcome);
            }
            if (payment.get("recharge_id") != null) {
                notifications.enqueue(customerId, succeeded ? "RECHARGE_SUCCEEDED" : "RECHARGE_FAILED",
                        succeeded ? "Recharge successful" : "Recharge failed",
                        "Your recharge " + outcome.toLowerCase(java.util.Locale.ROOT) + ".",
                        "recharge-payment-" + paymentId + "-" + outcome);
            }
            if (payment.get("subscription_id") != null) {
                notifications.enqueue(customerId, succeeded ? "SUBSCRIPTION_ACTIVE" : "SUBSCRIPTION_RENEWAL_FAILED",
                        succeeded ? "Subscription active" : "Subscription payment failed",
                        "Your subscription payment " + outcome.toLowerCase(java.util.Locale.ROOT) + ".",
                        "subscription-payment-" + paymentId + "-" + outcome);
            }
        }

    private boolean succeeded(String outcome) {
        return "SUCCEEDED".equals(outcome);
    }

    private Map<String, Object> findPayment(long paymentId, boolean lock) {
        String sql = "SELECT id, customer_id, recharge_id, booking_id, subscription_id, amount, currency, "
                + "status, provider_reference, created_at FROM payment_record WHERE id = ?"
                + (lock ? " FOR UPDATE" : "");
        try {
            return jdbcTemplate.queryForMap(sql, paymentId);
        } catch (EmptyResultDataAccessException exception) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Payment was not found");
        }
    }

    private Map<String, Object> findCallback(String callbackId) {
        try {
            return jdbcTemplate.queryForMap(
                    "SELECT callback_id, payment_id, outcome FROM payment_callback WHERE callback_id = ?",
                    callbackId);
        } catch (EmptyResultDataAccessException exception) {
            return null;
        }
    }

    private Map<String, Object> findRefund(String idempotencyKey) {
        try {
            return jdbcTemplate.queryForMap(
                    "SELECT id, payment_id, amount, status, reason, created_at FROM refund_record "
                            + "WHERE idempotency_key = ?",
                    idempotencyKey);
        } catch (EmptyResultDataAccessException exception) {
            return null;
        }
    }

    private PaymentResponse getPayment(long paymentId) {
        return jdbcTemplate.queryForObject(
                "SELECT id, amount, currency, status, provider_reference, created_at "
                        + "FROM payment_record WHERE id = ?",
                (result, row) -> toPaymentResponse(
                        result.getLong("id"),
                        result.getBigDecimal("amount"),
                        result.getString("currency"),
                        result.getString("status"),
                        result.getString("provider_reference"),
                        result.getTimestamp("created_at")),
                paymentId);
    }

    private long customerId(long userId) {
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT id FROM customer_profile WHERE user_id = ?", Long.class, userId);
        } catch (EmptyResultDataAccessException exception) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Customer profile was not found");
        }
    }

    private PaymentResponse response(Map<String, Object> payment) {
        return toPaymentResponse(
                ((Number) payment.get("id")).longValue(),
                (BigDecimal) payment.get("amount"),
                (String) payment.get("currency"),
                (String) payment.get("status"),
                (String) payment.get("provider_reference"),
                (Timestamp) payment.get("created_at"));
    }

    private PaymentResponse toPaymentResponse(
            long id,
            BigDecimal amount,
            String currency,
            String status,
            String providerReference,
            Timestamp createdAt) {
        return new PaymentResponse(
                id, amount, currency, status, providerReference, toLocalDateTime(createdAt));
    }

    private RefundResponse toRefundResponse(Map<String, Object> refund) {
        return new RefundResponse(
                ((Number) refund.get("id")).longValue(),
                ((Number) refund.get("payment_id")).longValue(),
                (BigDecimal) refund.get("amount"),
                (String) refund.get("status"),
                (String) refund.get("reason"),
                toLocalDateTime((Timestamp) refund.get("created_at")));
    }

    private LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }
}
