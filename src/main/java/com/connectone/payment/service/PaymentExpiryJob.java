package com.connectone.payment.service;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import com.connectone.admin.service.CheckoutCouponService;
import com.connectone.common.metrics.BusinessMetrics;

/**
 * Expires payments that were initiated but never confirmed. Only payments created inside the look-back window are
 * touched: anything older that is still INITIATED is an anomaly for a human (see the reconciliation screen), not
 * something a job should silently rewrite. Linked business records are released: recharge -> EXPIRED,
 * booking -> EXPIRED with its seat freed, coupon redemption given back.
 */
@Component
public class PaymentExpiryJob {

    private static final Logger LOGGER = LoggerFactory.getLogger(PaymentExpiryJob.class);

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate tx;
    private final CheckoutCouponService coupons;
    private final BusinessMetrics metrics;
    private final int expiryMinutes;
    private final int lookbackDays;

    public PaymentExpiryJob(JdbcTemplate jdbcTemplate, TransactionTemplate tx, CheckoutCouponService coupons,
            BusinessMetrics metrics,
            @Value("${connectone.payment.expiry-minutes:30}") int expiryMinutes,
            @Value("${connectone.payment.expiry-lookback-days:7}") int lookbackDays) {
        this.jdbcTemplate = jdbcTemplate;
        this.tx = tx;
        this.coupons = coupons;
        this.metrics = metrics;
        this.expiryMinutes = expiryMinutes;
        this.lookbackDays = lookbackDays;
    }

    @Scheduled(fixedDelayString = "${connectone.payment.expiry-scan-ms:60000}")
    public void expireStalePayments() {
        Integer expired = tx.execute(status -> {
            List<Map<String, Object>> due = jdbcTemplate.queryForList(
                    "SELECT id, recharge_id, booking_id, subscription_id FROM payment_record "
                            + "WHERE status = 'INITIATED' AND created_at < CURRENT_TIMESTAMP - make_interval(mins => ?) "
                            + "AND created_at > CURRENT_TIMESTAMP - make_interval(days => ?) "
                            + "ORDER BY id LIMIT 100 FOR UPDATE SKIP LOCKED", expiryMinutes, lookbackDays);
            for (Map<String, Object> payment : due) {
                long id = ((Number) payment.get("id")).longValue();
                jdbcTemplate.update("UPDATE payment_record SET status = 'EXPIRED', updated_at = CURRENT_TIMESTAMP WHERE id = ?", id);
                if (payment.get("recharge_id") != null) {
                    jdbcTemplate.update("UPDATE recharge_transaction SET status = 'EXPIRED', updated_at = CURRENT_TIMESTAMP "
                            + "WHERE id = ? AND status = 'PENDING'", payment.get("recharge_id"));
                }
                if (payment.get("booking_id") != null) {
                    jdbcTemplate.update("UPDATE booking_record SET status = 'EXPIRED', updated_at = CURRENT_TIMESTAMP, version = version + 1 "
                            + "WHERE id = ? AND status = 'PENDING'", payment.get("booking_id"));
                    jdbcTemplate.update("DELETE FROM booking_seat_claim WHERE booking_id = ?", payment.get("booking_id"));
                }
                coupons.release(id);
                metrics.count("payment.expired");
            }
            return due.size();
        });
        if (expired != null && expired > 0) {
            LOGGER.info("event=payments_expired count={}", expired);
        }
    }
}
