package com.connectone;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.connectone.booking.dto.BookingDtos.CreateRequest;
import com.connectone.booking.service.BookingService;
import com.connectone.common.exception.ApiException;
import com.connectone.payment.dto.PaymentDtos.ConfirmPaymentRequest;
import com.connectone.payment.dto.PaymentDtos.RefundRequest;
import com.connectone.payment.service.PaymentExpiryJob;
import com.connectone.payment.service.PaymentService;
import com.connectone.subscription.service.SubscriptionService;
import com.connectone.telecom.service.RechargeService;

/**
 * PostgreSQL integration tests (database connectone_test). They call the services directly and race them from
 * many threads: these are the guarantees (row locks, unique indexes, CHECK/FK constraints, rollbacks) that H2
 * could not prove. Run with scripts/test.ps1.
 */
@Tag("postgres")
@SpringBootTest
@ActiveProfiles("test")
@Import(TestDatabaseConfiguration.class)
class PostgresConcurrencyAndConstraintTests {

    @Autowired JdbcTemplate jdbc;
    @Autowired RechargeService recharges;
    @Autowired PaymentService payments;
    @Autowired BookingService bookings;
    @Autowired SubscriptionService subscriptions;
    @Autowired PaymentExpiryJob expiryJob;

    // ------------------------------------------------------------------ fixtures
    record Customer(long userId, long customerId, long mobileId) {
    }

    private Customer customer() {
        String tag = UUID.randomUUID().toString().substring(0, 8);
        long userId = jdbc.queryForObject(
                "INSERT INTO app_user (email, password_hash) VALUES (?, 'x') RETURNING id", Long.class, "pg-" + tag + "@t.test");
        long customerId = jdbc.queryForObject(
                "INSERT INTO customer_profile (user_id, first_name, last_name) VALUES (?, 'Test', 'User') RETURNING id", Long.class, userId);
        long mobileId = jdbc.queryForObject(
                "INSERT INTO mobile_number (customer_id, phone_number, is_primary) VALUES (?, ?, TRUE) RETURNING id", Long.class,
                customerId, "+9199" + (10000000L + Math.abs(tag.hashCode()) % 89999999L));
        return new Customer(userId, customerId, mobileId);
    }

    private long planId() {
        return jdbc.queryForObject("SELECT id FROM recharge_plan WHERE plan_code = 'RCH-MONTHLY-2GB'", Long.class);
    }

    private long couponWithLimit(int limit, int percent) {
        String tag = UUID.randomUUID().toString().substring(0, 8);
        long offer = jdbc.queryForObject(
                "INSERT INTO offer (offer_code, name, discount_percent, starts_at, ends_at) VALUES (?, 'T', ?, "
                        + "CURRENT_TIMESTAMP - INTERVAL '1 day', CURRENT_TIMESTAMP + INTERVAL '1 day') RETURNING id", Long.class, "O-" + tag, percent);
        return jdbc.queryForObject("INSERT INTO coupon (coupon_code, offer_id, usage_limit) VALUES (?, ?, ?) RETURNING id", Long.class, "C-" + tag, offer, limit);
    }

    private String couponCode(long couponId) {
        return jdbc.queryForObject("SELECT coupon_code FROM coupon WHERE id = ?", String.class, couponId);
    }

    /** Runs the callables at the same instant; returns how many succeeded and how many raised each ApiException status. */
    private Outcome race(List<Callable<Object>> work) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(work.size());
        CountDownLatch ready = new CountDownLatch(work.size());
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Object>> futures = new ArrayList<>();
        for (Callable<Object> task : work) {
            futures.add(pool.submit(() -> {
                ready.countDown();
                go.await();
                return task.call();
            }));
        }
        ready.await();
        go.countDown();
        Outcome outcome = new Outcome();
        for (Future<Object> future : futures) {
            try {
                future.get(60, TimeUnit.SECONDS);
                outcome.ok.incrementAndGet();
            } catch (java.util.concurrent.ExecutionException e) {
                if (e.getCause() instanceof ApiException api) {
                    outcome.failed.incrementAndGet();
                    outcome.statuses.add(api.getStatus().value());
                } else {
                    outcome.unexpected.add(e.getCause());
                }
            }
        }
        pool.shutdownNow();
        return outcome;
    }

    static class Outcome {
        final AtomicInteger ok = new AtomicInteger();
        final AtomicInteger failed = new AtomicInteger();
        final List<Integer> statuses = new java.util.concurrent.CopyOnWriteArrayList<>();
        final List<Throwable> unexpected = new java.util.concurrent.CopyOnWriteArrayList<>();
    }

    private long succeededRechargePayment(Customer c) {
        var recharge = recharges.initiate(c.userId(), c.mobileId(), planId(), "rk-" + UUID.randomUUID(), null);
        payments.confirm(c.userId(), recharge.paymentId(), new ConfirmPaymentRequest("cb-" + UUID.randomUUID(), "SUCCEEDED"));
        return recharge.paymentId();
    }

    // ------------------------------------------------------------------ concurrency
    @Test
    void concurrentRefundsNeverExceedThePaymentAmount() throws Exception {
        Customer c = customer();
        long paymentId = succeededRechargePayment(c);   // 299.00
        List<Callable<Object>> work = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            String key = "refund-" + UUID.randomUUID();
            work.add(() -> payments.refund(c.userId(), paymentId, new RefundRequest(new BigDecimal("100.00"), "race", key)));
        }
        Outcome outcome = race(work);

        BigDecimal refunded = jdbc.queryForObject(
                "SELECT COALESCE(SUM(amount), 0) FROM refund_record WHERE payment_id = ? AND status = 'SUCCEEDED'", BigDecimal.class, paymentId);
        assertThat(outcome.unexpected).isEmpty();
        assertThat(outcome.ok.get()).isEqualTo(2);                      // 2 x 100 fits in 299, the 3rd would make 300
        assertThat(refunded).isEqualByComparingTo("200.00");
        assertThat(outcome.statuses).containsOnly(400);
    }

    @Test
    void duplicateCallbacksAreAppliedExactlyOnce() throws Exception {
        Customer c = customer();
        var recharge = recharges.initiate(c.userId(), c.mobileId(), planId(), "rk-" + UUID.randomUUID(), null);
        String callbackId = "cb-" + UUID.randomUUID();
        List<Callable<Object>> work = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            work.add(() -> payments.confirm(c.userId(), recharge.paymentId(), new ConfirmPaymentRequest(callbackId, "SUCCEEDED")));
        }
        Outcome outcome = race(work);

        assertThat(outcome.unexpected).isEmpty();
        assertThat(outcome.ok.get()).isEqualTo(8);                      // idempotent: every duplicate answers 200
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM payment_callback WHERE payment_id = ?", Integer.class, recharge.paymentId())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT status FROM recharge_transaction WHERE id = ?", String.class, recharge.id())).isEqualTo("SUCCEEDED");
        // exactly one notification event was written to the outbox, however many duplicate callbacks raced
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM outbox_event WHERE aggregate_id = ? AND payload->>'type' = 'PAYMENT_SUCCEEDED'",
                Integer.class, Long.toString(c.customerId()))).isEqualTo(1);
    }

    @Test
    void sameIdempotencyKeyRechargeCreatesOnePaymentUnderConcurrency() throws Exception {
        Customer c = customer();
        String key = "rk-" + UUID.randomUUID();
        List<Callable<Object>> work = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            work.add(() -> recharges.initiate(c.userId(), c.mobileId(), planId(), key, null));
        }
        Outcome outcome = race(work);

        // Whatever the losers return, the database must still contain exactly one recharge and one payment.
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM recharge_transaction WHERE idempotency_key = ?", Integer.class, key)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM payment_record WHERE customer_id = ?", Integer.class, c.customerId())).isEqualTo(1);
        assertThat(outcome.ok.get()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void onlyOneCustomerWinsTheSameSeat() throws Exception {
        long item = jdbc.queryForObject(
                "INSERT INTO booking_catalog_item (item_type, item_code, title, starts_at, capacity, price, category) "
                        + "VALUES ('EVENT', ?, 'Race', CURRENT_TIMESTAMP + INTERVAL '3 days', 20, 100, 'EVENT') RETURNING id", Long.class, "T-" + UUID.randomUUID());
        List<Callable<Object>> work = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            Customer c = customer();
            work.add(() -> bookings.create(c.userId(), new CreateRequest(item, "7", "bk-" + UUID.randomUUID(), null)));
        }
        Outcome outcome = race(work);

        assertThat(outcome.unexpected).isEmpty();
        assertThat(outcome.ok.get()).isEqualTo(1);
        assertThat(outcome.statuses).containsOnly(409);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM booking_seat_claim WHERE catalog_item_id = ? AND seat_or_slot = '7'", Integer.class, item)).isEqualTo(1);
    }

    @Test
    void couponUsageLimitHoldsUnderConcurrentRedemption() throws Exception {
        long coupon = couponWithLimit(3, 10);
        String code = couponCode(coupon);
        List<Callable<Object>> work = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            Customer c = customer();
            work.add(() -> recharges.initiate(c.userId(), c.mobileId(), planId(), "rk-" + UUID.randomUUID(), code));
        }
        Outcome outcome = race(work);

        assertThat(outcome.unexpected).isEmpty();
        assertThat(outcome.ok.get()).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT usage_count FROM coupon WHERE id = ?", Integer.class, coupon)).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM coupon_redemption WHERE coupon_id = ?", Integer.class, coupon)).isEqualTo(3);
        // the 5 rejected checkouts rolled back completely: no orphan recharge/payment rows for them
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM payment_record WHERE coupon_id = ?", Integer.class, coupon)).isEqualTo(3);
    }

    @Test
    void concurrentRenewalsWithDistinctKeysCreateASinglePendingPayment() throws Exception {
        Customer c = customer();
        long plan = jdbc.queryForObject("SELECT id FROM subscription_plan WHERE active ORDER BY id LIMIT 1", Long.class);
        long sub = jdbc.queryForObject(
                "INSERT INTO customer_subscription (customer_id, plan_id, status, starts_at, expires_at) "
                        + "VALUES (?, ?, 'ACTIVE', CURRENT_TIMESTAMP - INTERVAL '5 days', CURRENT_TIMESTAMP + INTERVAL '5 days') RETURNING id", Long.class, c.customerId(), plan);
        List<Callable<Object>> work = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            String key = "renew-" + UUID.randomUUID();
            work.add(() -> subscriptions.renew(c.userId(), sub, key));
        }
        Outcome outcome = race(work);

        assertThat(outcome.unexpected).isEmpty();
        assertThat(outcome.ok.get()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM payment_record WHERE subscription_id = ? AND status = 'INITIATED'", Integer.class, sub)).isEqualTo(1);
    }

    // ------------------------------------------------------------------ constraints, coupons, rollback, jobs
    @Test
    void databaseConstraintsRejectInvalidRows() {
        Customer c = customer();
        Customer other = customer();
        long rechargeId = recharges.initiate(c.userId(), c.mobileId(), planId(), "rk-" + UUID.randomUUID(), null).id();

        // unique: a phone number belongs to exactly one mobile_number row
        assertThatThrownBy(() -> jdbc.update("INSERT INTO mobile_number (customer_id, phone_number) SELECT ?, phone_number FROM mobile_number WHERE id = ?",
                other.customerId(), c.mobileId())).isInstanceOf(DataIntegrityViolationException.class);
        // check: a payment must reference exactly one business parent
        assertThatThrownBy(() -> jdbc.update("INSERT INTO payment_record (customer_id, amount, status, idempotency_key) VALUES (?, 10, 'INITIATED', ?)",
                c.customerId(), "k-" + UUID.randomUUID())).isInstanceOf(DataIntegrityViolationException.class);
        // check: amount must be positive; status must be a known value
        assertThatThrownBy(() -> jdbc.update("INSERT INTO payment_record (customer_id, recharge_id, amount, status, idempotency_key) VALUES (?, ?, 0, 'INITIATED', ?)",
                c.customerId(), rechargeId, "k-" + UUID.randomUUID())).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE payment_record SET status = 'BOGUS' WHERE recharge_id = ?", rechargeId))
                .isInstanceOf(DataIntegrityViolationException.class);
        // foreign key (ON DELETE RESTRICT): a customer with payments cannot be deleted
        assertThatThrownBy(() -> jdbc.update("DELETE FROM customer_profile WHERE id = ?", c.customerId())).isInstanceOf(DataIntegrityViolationException.class);
        // one redemption per customer per coupon
        long coupon = couponWithLimit(5, 10);
        jdbc.update("INSERT INTO coupon_redemption (coupon_id, customer_id) VALUES (?, ?)", coupon, c.customerId());
        assertThatThrownBy(() -> jdbc.update("INSERT INTO coupon_redemption (coupon_id, customer_id) VALUES (?, ?)", coupon, c.customerId()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void couponDiscountIsAppliedToThePaymentAndReleasedWhenPaymentFails() {
        Customer c = customer();
        long coupon = couponWithLimit(5, 10);
        var recharge = recharges.initiate(c.userId(), c.mobileId(), planId(), "rk-" + UUID.randomUUID(), couponCode(coupon));

        assertThat(jdbc.queryForObject("SELECT amount FROM payment_record WHERE id = ?", BigDecimal.class, recharge.paymentId())).isEqualByComparingTo("269.10");
        assertThat(jdbc.queryForObject("SELECT discount_amount FROM payment_record WHERE id = ?", BigDecimal.class, recharge.paymentId())).isEqualByComparingTo("29.90");
        assertThat(jdbc.queryForObject("SELECT usage_count FROM coupon WHERE id = ?", Integer.class, coupon)).isEqualTo(1);

        payments.confirm(c.userId(), recharge.paymentId(), new ConfirmPaymentRequest("cb-" + UUID.randomUUID(), "FAILED"));
        assertThat(jdbc.queryForObject("SELECT usage_count FROM coupon WHERE id = ?", Integer.class, coupon)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM coupon_redemption WHERE coupon_id = ?", Integer.class, coupon)).isZero();
    }

    @Test
    void invalidCouponRollsBackTheWholeCheckout() {
        Customer c = customer();
        assertThatThrownBy(() -> recharges.initiate(c.userId(), c.mobileId(), planId(), "rk-" + UUID.randomUUID(), "NO-SUCH-COUPON"))
                .isInstanceOf(ApiException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM recharge_transaction WHERE customer_id = ?", Integer.class, c.customerId())).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM payment_record WHERE customer_id = ?", Integer.class, c.customerId())).isZero();
    }

    @Test
    void staleInitiatedPaymentsExpireAndReleaseTheirBusinessRecords() {
        Customer c = customer();
        var recharge = recharges.initiate(c.userId(), c.mobileId(), planId(), "rk-" + UUID.randomUUID(), null);
        jdbc.update("UPDATE payment_record SET created_at = CURRENT_TIMESTAMP - INTERVAL '2 hours' WHERE id = ?", recharge.paymentId());

        expiryJob.expireStalePayments();

        assertThat(jdbc.queryForObject("SELECT status FROM payment_record WHERE id = ?", String.class, recharge.paymentId())).isEqualTo("EXPIRED");
        assertThat(jdbc.queryForObject("SELECT status FROM recharge_transaction WHERE id = ?", String.class, recharge.id())).isEqualTo("EXPIRED");
    }
}
