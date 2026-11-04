package com.connectone.admin.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Map;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.connectone.common.exception.ApiException;
import com.connectone.common.metrics.BusinessMetrics;

/**
 * Coupon rules applied at checkout: coupon and offer active, inside the validity window, usage limit not reached,
 * one redemption per customer. The discount never reduces a payment below {@link #MINIMUM_PAYABLE}.
 * A coupon consumed by a payment that later fails/expires is released again (see {@link #release(long)}).
 */
@Service
public class CheckoutCouponService {

    static final BigDecimal MINIMUM_PAYABLE = new BigDecimal("1.00");

    private final JdbcTemplate jdbcTemplate;
    private final BusinessMetrics metrics;

    public CheckoutCouponService(JdbcTemplate jdbcTemplate, BusinessMetrics metrics) {
        this.jdbcTemplate = jdbcTemplate;
        this.metrics = metrics;
    }

    public record Quote(String couponCode, BigDecimal discountPercent, BigDecimal discountAmount, BigDecimal payable) {
    }

    /** Side-effect free preview for the checkout screen. */
    @Transactional(readOnly = true)
    public Quote quote(long customerId, String code, BigDecimal amount) {
        Map<String, Object> coupon = loadCoupon(code, false);
        validate(coupon, customerId);
        return price(code, (BigDecimal) coupon.get("discount_percent"), amount);
    }

    /** Redeems the coupon for the (just created) payment identified by its idempotency key. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void applyToPayment(long customerId, String code, String paymentIdempotencyKey, BigDecimal amount) {
        if (code == null || code.isBlank()) {
            return;
        }
        Map<String, Object> coupon = loadCoupon(code.strip(), true);
        validate(coupon, customerId);
        Quote quote = price(code.strip(), (BigDecimal) coupon.get("discount_percent"), amount);
        long paymentId = jdbcTemplate.queryForObject(
                "SELECT id FROM payment_record WHERE idempotency_key = ?", Long.class, paymentIdempotencyKey);
        try {
            jdbcTemplate.update(
                    "INSERT INTO coupon_redemption (coupon_id, customer_id, payment_id, discount_amount) VALUES (?, ?, ?, ?)",
                    coupon.get("id"), customerId, paymentId, quote.discountAmount());
        } catch (DuplicateKeyException exception) {
            throw new ApiException(HttpStatus.CONFLICT, "Coupon has already been redeemed by this customer");
        }
        jdbcTemplate.update("UPDATE coupon SET usage_count = usage_count + 1 WHERE id = ?", coupon.get("id"));
        jdbcTemplate.update(
                "UPDATE payment_record SET amount = ?, discount_amount = ?, coupon_id = ? WHERE id = ?",
                quote.payable(), quote.discountAmount(), coupon.get("id"), paymentId);
        metrics.count("coupon.redeemed");
    }

    /** Gives the coupon back when its payment did not complete. Safe to call for payments without a coupon. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void release(long paymentId) {
        Long couponId = jdbcTemplate.query(
                "DELETE FROM coupon_redemption WHERE payment_id = ? RETURNING coupon_id",
                result -> result.next() ? result.getLong(1) : null, paymentId);
        if (couponId != null) {
            jdbcTemplate.update("UPDATE coupon SET usage_count = GREATEST(usage_count - 1, 0) WHERE id = ?", couponId);
            metrics.count("coupon.released");
        }
    }

    private Map<String, Object> loadCoupon(String code, boolean lock) {
        try {
            return jdbcTemplate.queryForMap(
                    "SELECT c.id, c.usage_count, c.usage_limit, c.active, o.discount_percent, "
                            + "o.starts_at, o.ends_at, o.active AS offer_active FROM coupon c "
                            + "JOIN offer o ON o.id = c.offer_id WHERE c.coupon_code = ?"
                            + (lock ? " FOR UPDATE OF c" : ""),
                    code);
        } catch (EmptyResultDataAccessException exception) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Coupon was not found");
        }
    }

    private void validate(Map<String, Object> coupon, long customerId) {
        LocalDateTime now = LocalDateTime.now();
        if (!Boolean.TRUE.equals(coupon.get("active")) || !Boolean.TRUE.equals(coupon.get("offer_active"))
                || !((Timestamp) coupon.get("starts_at")).toLocalDateTime().isBefore(now)
                || !((Timestamp) coupon.get("ends_at")).toLocalDateTime().isAfter(now)) {
            throw new ApiException(HttpStatus.CONFLICT, "Coupon is not active");
        }
        if (((Number) coupon.get("usage_count")).intValue() >= ((Number) coupon.get("usage_limit")).intValue()) {
            throw new ApiException(HttpStatus.CONFLICT, "Coupon usage limit has been reached");
        }
        Integer already = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM coupon_redemption WHERE coupon_id = ? AND customer_id = ?",
                Integer.class, coupon.get("id"), customerId);
        if (already != null && already > 0) {
            throw new ApiException(HttpStatus.CONFLICT, "Coupon has already been redeemed by this customer");
        }
    }

    static Quote price(String code, BigDecimal percent, BigDecimal amount) {
        BigDecimal discount = amount.multiply(percent).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
        BigDecimal maxDiscount = amount.subtract(MINIMUM_PAYABLE).max(BigDecimal.ZERO);
        discount = discount.min(maxDiscount);
        return new Quote(code, percent, discount, amount.subtract(discount));
    }
}
