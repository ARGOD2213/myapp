package com.connectone.common.config;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class DevelopmentSeedRunner implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;
    private final boolean enabled;
    private final String seedPassword;
    private final String datasourceUrl;
    private final String datasourceUsername;

    public DevelopmentSeedRunner(
            JdbcTemplate jdbcTemplate,
            PasswordEncoder passwordEncoder,
            @Value("${connectone.seed.enabled:false}") boolean enabled,
            @Value("${connectone.seed.password:}") String seedPassword,
            @Value("${spring.datasource.url}") String datasourceUrl,
            @Value("${spring.datasource.username}") String datasourceUsername) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
        this.enabled = enabled;
        this.seedPassword = seedPassword;
        this.datasourceUrl = datasourceUrl;
        this.datasourceUsername = datasourceUsername;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments arguments) {
        if (!enabled) {
            return;
        }
        validateSeedTarget();
        if (seedPassword == null || seedPassword.length() < 12 || seedPassword.length() > 72) {
            throw new IllegalStateException(
                    "Seed mode requires CONNECTONE_SEED_PASSWORD between 12 and 72 characters");
        }

        long adminUser = ensureUser("seed-admin@connectone.local", "ADMIN", "Asha", "Admin", null);
        long supportUser = ensureUser("seed-support@connectone.local", "SUPPORT_AGENT", "Sam", "Support", null);
        long firstCustomer = ensureUser(
                "seed-customer-1@connectone.local", "CUSTOMER", "Aarav", "Shah", "+15550001001");
        long secondCustomer = ensureUser(
                "seed-customer-2@connectone.local", "CUSTOMER", "Maya", "Rao", "+15550001002");
        long thirdCustomer = ensureUser(
                "seed-customer-3@connectone.local", "CUSTOMER", "Noah", "Patel", "+15550001003");

        long mobile1 = ensureMobile(firstCustomer, "+15550001001");
        long mobile2 = ensureMobile(secondCustomer, "+15550001002");
        long mobile3 = ensureMobile(thirdCustomer, "+15550001003");

        long planDaily = idFor("SELECT id FROM recharge_plan WHERE plan_code = 'RCH-DAILY-1GB'");
        long planMonthly = idFor("SELECT id FROM recharge_plan WHERE plan_code = 'RCH-MONTHLY-2GB'");
        long paymentSuccess = ensureRecharge(firstCustomer, mobile1, planDaily,
                "seed-recharge-success", "SUCCEEDED", "SUCCEEDED", new BigDecimal("29.00"));
        ensureRecharge(secondCustomer, mobile2, planMonthly,
                "seed-recharge-failed", "FAILED", "FAILED", new BigDecimal("299.00"));
        ensureRecharge(thirdCustomer, mobile3, planDaily,
                "seed-recharge-pending", "PENDING", "INITIATED", new BigDecimal("29.00"));
        ensureRefund(paymentSuccess);

        long monthlyPlan = idFor("SELECT id FROM subscription_plan WHERE plan_code = 'SUB-STREAM-MONTHLY'");
        long annualPlan = idFor("SELECT id FROM subscription_plan WHERE plan_code = 'SUB-STREAM-ANNUAL'");
        ensureSubscription(firstCustomer, monthlyPlan, "ACTIVE", "SUCCEEDED", "seed-subscription-active");
        ensureSubscription(secondCustomer, annualPlan, "RENEWAL_FAILED", "FAILED", "seed-subscription-failed");

        long bookingSchedule = idFor("SELECT id FROM booking_catalog_item WHERE item_code = 'DEMO-BUS-001'");
        ensureBooking(firstCustomer, bookingSchedule, "seed-booking-confirmed", "1", "CONFIRMED", "SUCCEEDED");
        ensureBooking(secondCustomer, bookingSchedule, "seed-booking-failed", "2", "CANCELLED", "FAILED");
        ensureBooking(thirdCustomer, bookingSchedule, "seed-booking-pending", "3", "PENDING", "INITIATED");

        long demoContent = idFor("SELECT id FROM streaming_content WHERE content_code = 'DEMO-MOVIES'");
        ensureWatchHistory(firstCustomer, demoContent);
        long openTicket = ensureTicket(firstCustomer, supportUser, "OPEN", "NORMAL", "seed-ticket-open");
        ensureTicketComment(openTicket, firstCustomer, "Synthetic customer support request", false);
        long resolvedTicket = ensureTicket(secondCustomer, supportUser, "RESOLVED", "HIGH", "seed-ticket-resolved");
        ensureTicketComment(resolvedTicket, supportUser, "Synthetic support resolution", false);

        ensureNotification(firstCustomer, "BOOKING_CONFIRMED", "Synthetic booking is confirmed",
                "seed-notification-booking");
        ensureNotification(secondCustomer, "SUPPORT_TICKET_UPDATED", "Synthetic ticket was resolved",
                "seed-notification-support");
        ensureNotification(thirdCustomer, "SUBSCRIPTION_EXPIRING", "Synthetic subscription reminder",
                "seed-notification-subscription");
        ensureSeedOffer(adminUser);
        ensureAudit(adminUser, "SYNTHETIC_SEED_APPLIED", "PLATFORM", "milestone-3");
    }

    private void validateSeedTarget() {
        boolean connectOnePostgres = (datasourceUrl.equals("jdbc:postgresql://localhost:5434/connectone_dev")
                || datasourceUrl.equals("jdbc:postgresql://localhost:5434/connectone_test"))
                && "connectone_app".equals(datasourceUsername);
        if (!connectOnePostgres) {
            throw new IllegalStateException(
                    "Seed mode is restricted to the dedicated ConnectOne PostgreSQL databases on port 5434");
        }
    }

    private long ensureUser(String email, String role, String firstName, String lastName, String phoneNumber) {
        Long existingId = jdbcTemplate.query(
                "SELECT id FROM app_user WHERE email = ?",
                result -> result.next() ? result.getLong(1) : null,
                email);
        long userId;
        if (existingId != null) {
            userId = existingId;
        } else {
            userId = insert(
                    "INSERT INTO app_user (email, password_hash, account_status) VALUES (?, ?, 'ACTIVE')",
                    email, passwordEncoder.encode(seedPassword));
        }
        Integer roleCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user_role ur JOIN app_role r ON r.id = ur.role_id "
                        + "WHERE ur.user_id = ? AND r.role_code = ?",
                Integer.class, userId, role);
        if (roleCount == null || roleCount == 0) {
            jdbcTemplate.update(
                    "INSERT INTO user_role (user_id, role_id) "
                            + "SELECT ?, id FROM app_role WHERE role_code = ?",
                    userId, role);
        }
        if ("CUSTOMER".equals(role)) {
            Long customerCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM customer_profile WHERE user_id = ?", Long.class, userId);
            if (customerCount == null || customerCount == 0) {
                jdbcTemplate.update(
                        "INSERT INTO customer_profile (user_id, first_name, last_name, phone_number) "
                                + "VALUES (?, ?, ?, ?)",
                        userId, firstName, lastName, phoneNumber);
            }
        }
        return userId;
    }

    private long ensureMobile(long userId, String phoneNumber) {
        long customerId = idFor("SELECT id FROM customer_profile WHERE user_id = ?", userId);
        Map<String, Object> existing = jdbcTemplate.query(
                "SELECT id, customer_id FROM mobile_number WHERE phone_number = ?",
                result -> result.next()
                        ? Map.of("id", result.getLong("id"), "customer_id", result.getLong("customer_id"))
                        : null,
                phoneNumber);
        if (existing != null) {
            if (((Number) existing.get("customer_id")).longValue() != customerId) {
                throw new IllegalStateException("Synthetic mobile number is already assigned to another customer");
            }
            return ((Number) existing.get("id")).longValue();
        }
        return insert(
                "INSERT INTO mobile_number (customer_id, phone_number) VALUES (?, ?)",
                customerId, phoneNumber);
    }

    private long ensureRecharge(
            long userId,
            long mobileNumberId,
            long planId,
            String idempotencyKey,
            String rechargeStatus,
            String paymentStatus,
            BigDecimal amount) {
        Long existing = jdbcTemplate.query(
                "SELECT id FROM recharge_transaction WHERE idempotency_key = ?",
                result -> result.next() ? result.getLong(1) : null,
                idempotencyKey);
        if (existing != null) {
            return idFor("SELECT id FROM payment_record WHERE recharge_id = ?", existing);
        }
        long customerId = idFor("SELECT id FROM customer_profile WHERE user_id = ?", userId);
        Long validityDays = jdbcTemplate.queryForObject(
                "SELECT validity_days FROM recharge_plan WHERE id = ?", Long.class, planId);
        LocalDateTime expiresAt = "SUCCEEDED".equals(rechargeStatus)
                ? LocalDateTime.now().plusDays(validityDays)
                : null;
        long rechargeId = insert(
                "INSERT INTO recharge_transaction "
                        + "(customer_id, mobile_number_id, plan_id, amount, status, idempotency_key, expires_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?)",
                customerId,
                mobileNumberId,
                planId,
                amount,
                rechargeStatus,
                idempotencyKey,
                expiresAt == null ? null : Timestamp.valueOf(expiresAt));
        long paymentId = insert(
                "INSERT INTO payment_record "
                        + "(customer_id, recharge_id, amount, currency, status, idempotency_key, provider_reference) "
                        + "VALUES (?, ?, ?, 'INR', ?, ?, ?)",
                customerId,
                rechargeId,
                amount,
                paymentStatus,
                "seed-payment-" + idempotencyKey,
                "INITIATED".equals(paymentStatus) ? null : "SIM-" + idempotencyKey);
        return paymentId;
    }

    private void ensureRefund(long paymentId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM refund_record WHERE idempotency_key = ?",
                Integer.class, "seed-refund-partial");
        if (count != null && count == 0) {
            jdbcTemplate.update(
                    "INSERT INTO refund_record (payment_id, amount, status, reason, idempotency_key) "
                            + "VALUES (?, ?, 'SUCCEEDED', ?, ?)",
                    paymentId, new BigDecimal("5.00"), "Synthetic partial refund exercise", "seed-refund-partial");
        }
    }

    private void ensureSubscription(
            long userId,
            long planId,
            String subscriptionStatus,
            String paymentStatus,
            String idempotencyKey) {
        long customerId = idFor("SELECT id FROM customer_profile WHERE user_id = ?", userId);
        Integer existing = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM customer_subscription WHERE customer_id = ? AND plan_id = ?",
                Integer.class, customerId, planId);
        if (existing != null && existing > 0) {
            return;
        }

        Integer billingDays = jdbcTemplate.queryForObject(
                "SELECT billing_period_days FROM subscription_plan WHERE id = ?", Integer.class, planId);
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiresAt = "ACTIVE".equals(subscriptionStatus)
                ? now.plusDays(billingDays - 5L)
                : now.plusDays(10);
        long subscriptionId = insert(
                "INSERT INTO customer_subscription "
                        + "(customer_id, plan_id, status, starts_at, expires_at, auto_renew) "
                        + "VALUES (?, ?, ?, ?, ?, ?)",
                customerId,
                planId,
                subscriptionStatus,
                Timestamp.valueOf(now.minusDays(5)),
                Timestamp.valueOf(expiresAt),
                "ACTIVE".equals(subscriptionStatus));
        BigDecimal price = jdbcTemplate.queryForObject(
                "SELECT price FROM subscription_plan WHERE id = ?", BigDecimal.class, planId);
        if ("FAILED".equals(paymentStatus)) {
            insert(
                    "INSERT INTO payment_record "
                            + "(customer_id, subscription_id, amount, currency, status, idempotency_key, provider_reference) "
                            + "VALUES (?, ?, ?, 'INR', 'SUCCEEDED', ?, ?)",
                    customerId,
                    subscriptionId,
                    price,
                    "seed-payment-" + idempotencyKey + "-initial",
                    "SIM-" + idempotencyKey + "-initial");
        }
        insert(
                "INSERT INTO payment_record "
                        + "(customer_id, subscription_id, amount, currency, status, idempotency_key, provider_reference) "
                        + "VALUES (?, ?, ?, 'INR', ?, ?, ?)",
                customerId,
                subscriptionId,
                price,
                paymentStatus,
                "seed-payment-" + idempotencyKey,
                "SUCCEEDED".equals(paymentStatus) ? "SIM-" + idempotencyKey : null);
        jdbcTemplate.update(
                "INSERT INTO subscription_event (subscription_id, event_type, details) VALUES (?, ?, ?)",
                subscriptionId, "SEEDED", "Synthetic engineering exercise record");
    }

        private void ensureBooking(
                long userId,
                long scheduleId,
                String idempotencyKey,
                String seat,
                String bookingStatus,
                String paymentStatus) {
            Integer exists = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM booking_record WHERE idempotency_key = ?",
                    Integer.class, idempotencyKey);
            if (exists != null && exists > 0) {
                return;
            }
            long customerId = idFor("SELECT id FROM customer_profile WHERE user_id = ?", userId);
            BigDecimal price = jdbcTemplate.queryForObject(
                    "SELECT price FROM booking_catalog_item WHERE id = ?", BigDecimal.class, scheduleId);
            long bookingId = insert(
                    "INSERT INTO booking_record "
                            + "(booking_code, customer_id, catalog_item_id, seat_or_slot, status, amount, idempotency_key) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?)",
                    "SEED-" + idempotencyKey,
                    customerId,
                    scheduleId,
                    seat,
                    bookingStatus,
                    price,
                    idempotencyKey);
            if (!"CANCELLED".equals(bookingStatus)) {
                jdbcTemplate.update(
                        "INSERT INTO booking_seat_claim (catalog_item_id, seat_or_slot, booking_id) VALUES (?, ?, ?)",
                        scheduleId, seat, bookingId);
            }
            insert(
                    "INSERT INTO payment_record "
                            + "(customer_id, booking_id, amount, currency, status, idempotency_key, provider_reference) "
                            + "VALUES (?, ?, ?, 'INR', ?, ?, ?)",
                    customerId,
                    bookingId,
                    price,
                    paymentStatus,
                    "seed-payment-" + idempotencyKey,
                    "INITIATED".equals(paymentStatus) ? null : "SIM-" + idempotencyKey);
        }

        private void ensureWatchHistory(long userId, long contentId) {
            long customerId = idFor("SELECT id FROM customer_profile WHERE user_id = ?", userId);
            Integer exists = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM streaming_watch_history WHERE customer_id = ? AND content_id = ?",
                    Integer.class, customerId, contentId);
            if (exists == null || exists == 0) {
                jdbcTemplate.update(
                        "INSERT INTO streaming_watch_history (customer_id, content_id, progress_seconds) "
                                + "VALUES (?, ?, 1200)",
                        customerId, contentId);
            }
        }

        private long ensureTicket(long userId, long supportUser, String status, String priority, String idempotencyKey) {
            Long existingId = jdbcTemplate.query(
                    "SELECT id FROM support_ticket WHERE ticket_code = ?",
                    result -> result.next() ? result.getLong(1) : null,
                    "SEED-" + idempotencyKey);
            if (existingId != null) {
                return existingId;
            }
            long customerId = idFor("SELECT id FROM customer_profile WHERE user_id = ?", userId);
            long id = insert(
                    "INSERT INTO support_ticket "
                            + "(ticket_code, customer_id, assigned_user_id, subject, description, category, priority, "
                            + "status, closed_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    "SEED-" + idempotencyKey,
                    customerId,
                    supportUser,
                    "Synthetic support request",
                    "Deterministic seed exercise record",
                    "GENERAL",
                    priority,
                    status,
                    "CLOSED".equals(status) ? Timestamp.valueOf(LocalDateTime.now()) : null);
            jdbcTemplate.update(
                    "INSERT INTO ticket_history (ticket_id, actor_user_id, event_type, details) "
                            + "VALUES (?, ?, 'SEEDED', 'Synthetic support history')",
                    id, supportUser);
            return id;
        }

        private void ensureTicketComment(long ticketId, long userId, String body, boolean internal) {
            Integer exists = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM ticket_comment WHERE ticket_id = ? AND body = ?",
                    Integer.class, ticketId, body);
            if (exists == null || exists == 0) {
                jdbcTemplate.update(
                        "INSERT INTO ticket_comment (ticket_id, author_user_id, body, is_internal) VALUES (?, ?, ?, ?)",
                        ticketId, userId, body, internal);
            }
        }

        private void ensureNotification(long userId, String event, String message, String idempotencyKey) {
            long customerId = idFor("SELECT id FROM customer_profile WHERE user_id = ?", userId);
            Integer exists = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM notification_record WHERE customer_id = ? AND idempotency_key = ?",
                    Integer.class, customerId, idempotencyKey);
            if (exists == null || exists == 0) {
                jdbcTemplate.update(
                        "INSERT INTO notification_record "
                                + "(customer_id, channel, event_type, status, subject, message, delivered_at, idempotency_key) "
                                + "VALUES (?, 'IN_APP', ?, 'SENT', 'Synthetic platform activity', ?, "
                                + "CURRENT_TIMESTAMP, ?)",
                        customerId, event, message, idempotencyKey);
            }
        }

        private void ensureSeedOffer(long adminUser) {
            Integer exists = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM offer WHERE offer_code = 'SEED-WELCOME'",
                    Integer.class);
            if (exists == null || exists == 0) {
                long offerId = insert(
                        "INSERT INTO offer (offer_code, name, discount_percent, starts_at, ends_at, active) "
                                + "VALUES ('SEED-WELCOME', 'Synthetic welcome offer', 10.00, ?, ?, TRUE)",
                        Timestamp.valueOf(LocalDateTime.now().minusDays(1)),
                        Timestamp.valueOf(LocalDateTime.now().plusDays(30)));
                insert(
                        "INSERT INTO coupon (coupon_code, offer_id, usage_limit) VALUES ('SEED10', ?, 100)",
                        offerId);
            }
            ensureAudit(adminUser, "SEED_OFFER_AVAILABLE", "OFFER", "SEED-WELCOME");
        }

        private void ensureAudit(long actorUser, String event, String type, String entityId) {
            Integer exists = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM audit_event WHERE event_type = ? AND entity_type = ? AND entity_id = ?",
                    Integer.class, event, type, entityId);
            if (exists == null || exists == 0) {
                jdbcTemplate.update(
                        "INSERT INTO audit_event (actor_user_id, event_type, entity_type, entity_id) VALUES (?, ?, ?, ?)",
                        actorUser, event, type, entityId);
            }
        }

    private long idFor(String sql, Object... args) {
        Long id = jdbcTemplate.queryForObject(sql, Long.class, args);
        if (id == null) {
            throw new IllegalStateException("Seed reference data is missing");
        }
        return id;
    }

    private long insert(String sql, Object... args) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(sql, new String[] {"id"});
            for (int index = 0; index < args.length; index++) {
                statement.setObject(index + 1, args[index]);
            }
            return statement;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("Database did not return an inserted identifier");
        }
        return key.longValue();
    }
}
