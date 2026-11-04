package com.connectone;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.connectone.common.config.DevelopmentSeedRunner;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestDatabaseConfiguration.class)
class ConnectOneApplicationTests {

    private static final String TEST_PASSWORD = "ConnectOne-Test-Password-1";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DevelopmentSeedRunner seedRunner;

    @Test
    void contextLoads() {
    }

    @Test
    void flywayInitializesCoreSchemaFromAnEmptyDatabase() {
        Integer migratedTables = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables "
                        + "WHERE table_schema = 'public' AND table_name IN "
                        + "('app_user', 'customer_profile', 'recharge_transaction', "
                        + "'payment_record', 'customer_subscription', 'booking_record', "
                        + "'support_ticket', 'refresh_token', 'otp_challenge', "
                        + "'customer_activity', 'payment_callback', 'booking_seat_claim', "
                        + "'streaming_content', 'ticket_history', 'coupon', 'access_token_revocation')",
                Integer.class);
        Integer successfulMigrations = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history "
                        + "WHERE success = TRUE AND type = 'SQL'",
                Integer.class);
        Integer seededPlans = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM recharge_plan WHERE active = TRUE", Integer.class);

        assertThat(migratedTables).isEqualTo(16);
        assertThat(successfulMigrations).isEqualTo(6);
        assertThat(seededPlans).isEqualTo(3);
    }

    @Test
    void listsAndFiltersActiveRechargePlansFromTheDatabase() throws Exception {
        mockMvc.perform(get("/api/v1/recharge-plans").param("maxPrice", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].code").value("RCH-DAILY-1GB"))
                .andExpect(jsonPath("$[0].price").value(29.0));
        mockMvc.perform(get("/api/v1/recharge-plans").param("maxPrice", "-1"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void enforcesAuthenticationRolesAndRefreshTokenRevocation() throws Exception {
        mockMvc.perform(get("/api/v1/customers/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Unauthorized"));

        TestUser customer = registerAndVerify();
        mockMvc.perform(get("/api/v1/customers/me")
                        .header("Authorization", bearer(customer.accessToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(customer.email()));
        mockMvc.perform(get("/api/v1/customers")
                        .header("Authorization", bearer(customer.accessToken())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Forbidden"));
        mockMvc.perform(get("/api/v1/customers/me/activity")
                        .header("Authorization", bearer(customer.accessToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));

        MvcResult refreshResult = mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new RefreshBody(customer.refreshToken()))))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode rotatedTokens = objectMapper.readTree(refreshResult.getResponse().getContentAsByteArray());
        String rotatedRefreshToken = rotatedTokens.path("refreshToken").asText();
        String rotatedAccessToken = rotatedTokens.path("accessToken").asText();
        mockMvc.perform(get("/api/v1/customers/me")
                        .header("Authorization", bearer(rotatedAccessToken)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new RefreshBody(customer.refreshToken()))))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/customers/me")
                        .header("Authorization", bearer(rotatedAccessToken)))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new RefreshBody(rotatedRefreshToken))))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new RefreshBody(rotatedRefreshToken))))
                .andExpect(status().isUnauthorized());

        MvcResult adminLogin = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new LoginBody("seed-admin@connectone.local", "test-only-seed-password"))))
                .andExpect(status().isOk())
                .andReturn();
        String adminToken = objectMapper.readTree(adminLogin.getResponse().getContentAsByteArray())
                .path("accessToken").asText();
        mockMvc.perform(get("/api/v1/customers").param("size", "5").param("sort", "createdAt,desc")
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    void rejectsInvalidRegistrationInput() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new RegisterBody(
                                "not-an-email", "short", "Test", "Person", "+15550001004"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void enforcesOtpAttemptLimitAndTemporaryAccountLockout() throws Exception {
        String email = "locked-" + UUID.randomUUID() + "@example.test";
        MvcResult registration = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new RegisterBody(email, TEST_PASSWORD, "Lock", "Test", uniquePhone()))))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode challenge = objectMapper.readTree(registration.getResponse().getContentAsByteArray());
        String challengeId = challenge.path("challengeId").asText();
        String correctOtp = challenge.path("developmentOtp").asText();
        for (int attempt = 0; attempt < 5; attempt++) {
            mockMvc.perform(post("/api/v1/auth/verify-otp")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(new OtpBody(challengeId, "000000".equals(correctOtp) ? "000001" : "000000"))))
                    .andExpect(status().isBadRequest());
        }
        assertThat(jdbcTemplate.queryForObject(
                "SELECT attempt_count FROM otp_challenge WHERE challenge_id = ?", Integer.class, challengeId))
                .isEqualTo(5);
        mockMvc.perform(post("/api/v1/auth/verify-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new OtpBody(challengeId, correctOtp))))
                .andExpect(status().isBadRequest());

        TestUser customer = registerAndVerify();
        for (int attempt = 0; attempt < 5; attempt++) {
            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(new LoginBody(customer.email(), "Wrong-Password-1"))))
                    .andExpect(status().isUnauthorized());
        }
        assertThat(jdbcTemplate.queryForObject(
                "SELECT account_status FROM app_user WHERE email = ?", String.class, customer.email()))
                .isEqualTo("LOCKED");
    }

    @Test
    void processesRechargePaymentRefundAndSubscriptionTransitions() throws Exception {
        TestUser customer = registerAndVerify();
        String token = bearer(customer.accessToken());
        MvcResult mobileResult = mockMvc.perform(post("/api/v1/mobile-numbers")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new PhoneBody(uniquePhone()))))
                .andExpect(status().isCreated())
                .andReturn();
        long mobileId = objectMapper.readTree(mobileResult.getResponse().getContentAsByteArray())
                .path("id").asLong();
        long rechargePlanId = jdbcTemplate.queryForObject(
                "SELECT id FROM recharge_plan WHERE plan_code = 'RCH-DAILY-1GB'", Long.class);

        RechargeBody rechargeRequest = new RechargeBody(mobileId, rechargePlanId, "recharge-" + UUID.randomUUID());
        MvcResult rechargeResult = mockMvc.perform(post("/api/v1/recharges")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(rechargeRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn();
        JsonNode recharge = objectMapper.readTree(rechargeResult.getResponse().getContentAsByteArray());
        long paymentId = recharge.path("paymentId").asLong();
        mockMvc.perform(post("/api/v1/recharges")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(rechargeRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(recharge.path("id").asLong()));

        ConfirmBody confirmation = new ConfirmBody("callback-" + UUID.randomUUID(), "SUCCEEDED");
        mockMvc.perform(post("/api/v1/payments/{id}/confirm", paymentId)
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(confirmation)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCEEDED"));
        mockMvc.perform(post("/api/v1/payments/{id}/confirm", paymentId)
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(confirmation)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCEEDED"));

        MvcResult notificationResult = mockMvc.perform(get("/api/v1/notifications/me")
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].eventType").value("RECHARGE_SUCCEEDED"))
                .andExpect(jsonPath("$[0].status").value("SENT"))
                .andReturn();
        long notificationId = objectMapper.readTree(notificationResult.getResponse().getContentAsByteArray())
                .get(0).path("id").asLong();
        mockMvc.perform(post("/api/v1/notifications/{id}/read", notificationId)
                        .header("Authorization", token))
                .andExpect(status().isNoContent());
        assertThat(jdbcTemplate.queryForObject(
                "SELECT read_at FROM notification_record WHERE id = ?", java.sql.Timestamp.class, notificationId))
                .isNotNull();

        RefundBody refund = new RefundBody(new BigDecimal("5.00"), "Customer requested partial refund",
                "refund-" + UUID.randomUUID());
        MvcResult refundResult = mockMvc.perform(post("/api/v1/payments/{id}/refunds", paymentId)
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(refund)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andReturn();
        long refundId = objectMapper.readTree(refundResult.getResponse().getContentAsByteArray())
                .path("id").asLong();
        mockMvc.perform(post("/api/v1/payments/{id}/refunds", paymentId)
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(refund)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(refundId));
        mockMvc.perform(get("/api/v1/recharges/me").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].status").value("SUCCEEDED"));
        mockMvc.perform(post("/api/v1/payments/{id}/refunds", paymentId)
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new RefundBody(
                                new BigDecimal("24.00"),
                                "Complete the simulated refund",
                                "refund-final-" + UUID.randomUUID()))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SUCCEEDED"));
        mockMvc.perform(get("/api/v1/payments/me").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("REFUNDED"));
        mockMvc.perform(get("/api/v1/recharges/me").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].status").value("REVERSED"));

        RechargeBody failedRechargeRequest =
                new RechargeBody(mobileId, rechargePlanId, "recharge-failed-" + UUID.randomUUID());
        MvcResult failedRechargeResult = mockMvc.perform(post("/api/v1/recharges")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(failedRechargeRequest)))
                .andExpect(status().isCreated())
                .andReturn();
        long failedPaymentId = objectMapper.readTree(failedRechargeResult.getResponse().getContentAsByteArray())
                .path("paymentId").asLong();
        ConfirmBody failure = new ConfirmBody("callback-" + UUID.randomUUID(), "FAILED");
        mockMvc.perform(post("/api/v1/payments/{id}/confirm", failedPaymentId)
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(failure)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"));
        mockMvc.perform(post("/api/v1/payments/{id}/refunds", failedPaymentId)
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new RefundBody(new BigDecimal("1.00"), "Not refundable",
                                "refund-failed-" + UUID.randomUUID()))))
                .andExpect(status().isConflict());

        long subscriptionPlanId = jdbcTemplate.queryForObject(
                "SELECT id FROM subscription_plan WHERE plan_code = 'SUB-STREAM-MONTHLY'", Long.class);
        MvcResult subscriptionResult = mockMvc.perform(post("/api/v1/subscriptions")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new PurchaseBody(subscriptionPlanId, false, "subscription-" + UUID.randomUUID()))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn();
        JsonNode subscription = objectMapper.readTree(subscriptionResult.getResponse().getContentAsByteArray());
        long subscriptionId = subscription.path("id").asLong();
        long subscriptionPaymentId = subscription.path("paymentId").asLong();
        mockMvc.perform(post("/api/v1/payments/{id}/confirm", subscriptionPaymentId)
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new ConfirmBody("callback-" + UUID.randomUUID(), "SUCCEEDED"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCEEDED"));
        mockMvc.perform(get("/api/v1/subscriptions/{id}/entitlement", subscriptionId)
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entitled").value(true));

        MvcResult renewalResult = mockMvc.perform(post("/api/v1/subscriptions/{id}/renew", subscriptionId)
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new IdempotencyBody("renewal-" + UUID.randomUUID()))))
                .andExpect(status().isOk())
                .andReturn();
        long renewalPaymentId = objectMapper.readTree(renewalResult.getResponse().getContentAsByteArray())
                .path("paymentId").asLong();
        mockMvc.perform(post("/api/v1/payments/{id}/confirm", renewalPaymentId)
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new ConfirmBody("callback-" + UUID.randomUUID(), "SUCCEEDED"))))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/subscriptions/{id}/cancel", subscriptionId)
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        mockMvc.perform(get("/api/v1/subscriptions/{id}/entitlement", subscriptionId)
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entitled").value(false));

        long annualPlanId = jdbcTemplate.queryForObject(
                "SELECT id FROM subscription_plan WHERE plan_code = 'SUB-STREAM-ANNUAL'", Long.class);
        MvcResult failedPurchase = mockMvc.perform(post("/api/v1/subscriptions")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new PurchaseBody(annualPlanId, false, "subscription-failed-" + UUID.randomUUID()))))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode failedSubscription = objectMapper.readTree(failedPurchase.getResponse().getContentAsByteArray());
        long failedSubscriptionId = failedSubscription.path("id").asLong();
        mockMvc.perform(post("/api/v1/payments/{id}/confirm", failedSubscription.path("paymentId").asLong())
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new ConfirmBody("callback-" + UUID.randomUUID(), "FAILED"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"));
        mockMvc.perform(get("/api/v1/subscriptions/{id}/entitlement", failedSubscriptionId)
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entitled").value(false));
    }

    @Test
    void seedsRepeatableSyntheticCustomerAndTransactionScenarios() {
        Integer customersBefore = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM customer_profile", Integer.class);
        Integer rechargesBefore = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM recharge_transaction", Integer.class);
        Integer paymentsBefore = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM payment_record", Integer.class);
        Integer subscriptionsBefore = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM customer_subscription", Integer.class);
        seedRunner.run(new DefaultApplicationArguments(new String[0]));
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM customer_profile", Integer.class))
                .isEqualTo(customersBefore);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM recharge_transaction", Integer.class))
                .isEqualTo(rechargesBefore);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM payment_record", Integer.class))
                .isEqualTo(paymentsBefore);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM customer_subscription", Integer.class))
                .isEqualTo(subscriptionsBefore);

        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM customer_profile", Integer.class))
                .isGreaterThanOrEqualTo(3);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM recharge_transaction", Integer.class))
                .isGreaterThanOrEqualTo(3);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM payment_record", Integer.class))
                .isGreaterThanOrEqualTo(5);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM refund_record", Integer.class))
                .isGreaterThanOrEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM customer_subscription", Integer.class))
                .isGreaterThanOrEqualTo(2);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM booking_record", Integer.class))
                .isGreaterThanOrEqualTo(3);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM streaming_watch_history", Integer.class))
                .isGreaterThanOrEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM support_ticket", Integer.class))
                .isGreaterThanOrEqualTo(2);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM notification_record", Integer.class))
                .isGreaterThanOrEqualTo(3);
    }

    @Test
    void protectsSeatReservationsAndProcessesBookingPaymentAndCancellation() throws Exception {
        TestUser first = registerAndVerify();
        TestUser second = registerAndVerify();
        long scheduleId = jdbcTemplate.queryForObject(
                "SELECT id FROM booking_catalog_item WHERE item_code = 'DEMO-BUS-001'", Long.class);

        BookingBody firstRequest = new BookingBody(scheduleId, "15", "booking-" + UUID.randomUUID());
        MvcResult created = mockMvc.perform(post("/api/v1/bookings")
                        .header("Authorization", bearer(first.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(firstRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn();
        JsonNode booking = objectMapper.readTree(created.getResponse().getContentAsByteArray());
        long bookingId = booking.path("id").asLong();
        long paymentId = booking.path("paymentId").asLong();
        mockMvc.perform(post("/api/v1/payments/{id}/confirm", paymentId)
                        .header("Authorization", bearer(second.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new ConfirmBody("foreign-callback-" + UUID.randomUUID(), "SUCCEEDED"))))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v1/bookings")
                        .header("Authorization", bearer(first.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(firstRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(bookingId));
        mockMvc.perform(post("/api/v1/bookings")
                        .header("Authorization", bearer(second.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new BookingBody(scheduleId, "15", "booking-" + UUID.randomUUID()))))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/api/v1/payments/{id}/confirm", paymentId)
                        .header("Authorization", bearer(first.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new ConfirmBody("booking-callback-" + UUID.randomUUID(), "SUCCEEDED"))))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/bookings/{id}/cancel", bookingId)
                        .header("Authorization", bearer(first.accessToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REFUNDED"));
        mockMvc.perform(post("/api/v1/bookings/{id}/cancel", bookingId)
                        .header("Authorization", bearer(first.accessToken())))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/api/v1/bookings/{id}/cancel", bookingId)
                        .header("Authorization", bearer(second.accessToken())))
                .andExpect(status().isNotFound());

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            BookingBody firstConcurrent = new BookingBody(scheduleId, "18", "booking-race-a-" + UUID.randomUUID());
            BookingBody secondConcurrent = new BookingBody(scheduleId, "18", "booking-race-b-" + UUID.randomUUID());
            Future<Integer> firstStatus = executor.submit(() ->
                    concurrentBooking(first.accessToken(), firstConcurrent, ready, start));
            Future<Integer> secondStatus = executor.submit(() ->
                    concurrentBooking(second.accessToken(), secondConcurrent, ready, start));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(List.of(firstStatus.get(15, TimeUnit.SECONDS), secondStatus.get(15, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(201, 409);
        } finally {
            executor.shutdownNow();
        }

        MvcResult failedBooking = mockMvc.perform(post("/api/v1/bookings")
                        .header("Authorization", bearer(second.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new BookingBody(scheduleId, "16", "booking-failed-" + UUID.randomUUID()))))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode failed = objectMapper.readTree(failedBooking.getResponse().getContentAsByteArray());
        mockMvc.perform(post("/api/v1/payments/{id}/confirm", failed.path("paymentId").asLong())
                        .header("Authorization", bearer(second.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new ConfirmBody("booking-failure-" + UUID.randomUUID(), "FAILED"))))
                .andExpect(status().isOk());
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM booking_record WHERE id = ?", String.class, failed.path("id").asLong()))
                .isEqualTo("CANCELLED");
    }

    @Test
    void enforcesStreamingEntitlementsAndSupportTicketIsolation() throws Exception {
        TestUser customer = registerAndVerify();
        long contentId = jdbcTemplate.queryForObject(
                "SELECT id FROM streaming_content WHERE content_code = 'DEMO-MOVIES'", Long.class);
        mockMvc.perform(post("/api/v1/streaming/content/{id}/play", contentId)
                        .header("Authorization", bearer(customer.accessToken())))
                .andExpect(status().isForbidden());

        TestUser entitled = loginSeed("seed-customer-1@connectone.local");
        mockMvc.perform(post("/api/v1/streaming/content/{id}/play", contentId)
                        .header("Authorization", bearer(entitled.accessToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.playbackUrl").exists());
        mockMvc.perform(get("/api/v1/streaming/history/me")
                        .header("Authorization", bearer(entitled.accessToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());

        MvcResult ticketResult = mockMvc.perform(post("/api/v1/support/tickets")
                        .header("Authorization", bearer(customer.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new SupportCreateBody(
                                "Test issue", "A synthetic support issue", "GENERAL", "NORMAL"))))
                .andExpect(status().isCreated())
                .andReturn();
        long ticketId = objectMapper.readTree(ticketResult.getResponse().getContentAsByteArray())
                .path("ticket").path("id").asLong();
        mockMvc.perform(get("/api/v1/support/tickets/{id}", ticketId)
                        .header("Authorization", bearer(entitled.accessToken())))
                .andExpect(status().isNotFound());

        TestUser agent = loginSeed("seed-support@connectone.local");
        mockMvc.perform(post("/api/v1/support/tickets/{id}/comments", ticketId)
                        .header("Authorization", bearer(agent.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new SupportCommentBody("Internal investigation", true))))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/support/tickets/{id}", ticketId)
                        .header("Authorization", bearer(customer.accessToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.comments.length()").value(0));
        mockMvc.perform(get("/api/v1/support/tickets/{id}", ticketId)
                        .header("Authorization", bearer(agent.accessToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.comments.length()").value(1));
        mockMvc.perform(put("/api/v1/support/tickets/{id}/status", ticketId)
                        .header("Authorization", bearer(agent.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new TicketStatusBody("RESOLVED"))))
                .andExpect(status().isConflict());
        mockMvc.perform(put("/api/v1/support/tickets/{id}/status", ticketId)
                        .header("Authorization", bearer(agent.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new TicketStatusBody("IN_PROGRESS"))))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/support/tickets/{id}/history", ticketId)
                        .header("Authorization", bearer(agent.accessToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void protectsAdminOperationsAndRedeemsCouponsOnce() throws Exception {
        TestUser customer = registerAndVerify();
        mockMvc.perform(get("/api/v1/admin/recharge-plans")
                        .header("Authorization", bearer(customer.accessToken())))
                .andExpect(status().isForbidden());

        TestUser admin = loginSeed("seed-admin@connectone.local");
        mockMvc.perform(get("/api/v1/admin/recharge-plans")
                        .header("Authorization", bearer(admin.accessToken())))
                .andExpect(status().isOk());
        LocalDateTime start = LocalDateTime.now().minusMinutes(1);
        LocalDateTime end = LocalDateTime.now().plusDays(1);
        MvcResult offer = mockMvc.perform(post("/api/v1/admin/offers")
                        .header("Authorization", bearer(admin.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new OfferBody("OFF-" + UUID.randomUUID().toString().substring(0, 12), "Test offer",
                                new BigDecimal("15.00"), start, end, true))))
                .andExpect(status().isCreated())
                .andReturn();
        long offerId = objectMapper.readTree(offer.getResponse().getContentAsByteArray()).path("id").asLong();
        long categoryId = jdbcTemplate.queryForObject(
                "SELECT id FROM streaming_category WHERE category_code = 'MOVIES'", Long.class);
        MvcResult content = mockMvc.perform(post("/api/v1/streaming/admin/content")
                        .header("Authorization", bearer(admin.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new StreamContentBody(categoryId, "TEST-" + UUID.randomUUID().toString().substring(0, 8),
                                "Admin-managed content", "Synthetic metadata", "MOVIE", "GENERAL", 60,
                                "https://media.example.test/admin", true))))
                .andExpect(status().isOk())
                .andReturn();
        long contentId = objectMapper.readTree(content.getResponse().getContentAsByteArray()).path("id").asLong();
        mockMvc.perform(put("/api/v1/streaming/admin/content/{id}", contentId)
                        .header("Authorization", bearer(admin.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new StreamContentBody(categoryId, "TEST-" + UUID.randomUUID().toString().substring(0, 8),
                                "Updated admin content", "Updated synthetic metadata", "MOVIE", "GENERAL", 60,
                                "https://media.example.test/admin-updated", false))))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/streaming/admin/content")
                        .header("Authorization", bearer(customer.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new StreamContentBody(categoryId, "UNAUTH-01", "Unauthorized", "No access",
                                "MOVIE", "GENERAL", 60, "https://media.example.test/no", true))))
                .andExpect(status().isForbidden());
        String couponCode = "CPN-" + UUID.randomUUID().toString().substring(0, 12);
        mockMvc.perform(post("/api/v1/admin/offers/{id}/coupons", offerId)
                        .header("Authorization", bearer(admin.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new CouponBody(couponCode, 2))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/coupons/redeem")
                        .header("Authorization", bearer(customer.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new RedeemBody(couponCode))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.discountPercent").value(15.0));
        mockMvc.perform(post("/api/v1/coupons/redeem")
                        .header("Authorization", bearer(customer.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new RedeemBody(couponCode))))
                .andExpect(status().isConflict());

        long customerId = jdbcTemplate.queryForObject(
                "SELECT id FROM customer_profile WHERE user_id = "
                        + "(SELECT id FROM app_user WHERE email = ?)",
                Long.class, customer.email());
        jdbcTemplate.update(
                "INSERT INTO notification_record "
                        + "(customer_id, channel, event_type, status, message, attempt_count, next_attempt_at) "
                        + "VALUES (?, 'IN_APP', 'TEST_RETRY', 'FAILED', 'Retry test', 1, CURRENT_TIMESTAMP)",
                customerId);
        mockMvc.perform(post("/api/v1/admin/notifications/retry")
                        .header("Authorization", bearer(admin.accessToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.processed").value(1));
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM notification_record WHERE customer_id = ? AND event_type = 'TEST_RETRY'",
                String.class, customerId)).isEqualTo("SENT");
    }

    @Test
    void logoutRevokesAccessAndRefreshTokenReuseRevokesRotatedAccess() throws Exception {
        TestUser customer = registerAndVerify();
        mockMvc.perform(post("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new RefreshBody(customer.refreshToken()))))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/customers/me")
                        .header("Authorization", bearer(customer.accessToken())))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new RefreshBody(customer.refreshToken()))))
                .andExpect(status().isUnauthorized());
    }

    private TestUser registerAndVerify() throws Exception {
        String email = "customer-" + UUID.randomUUID() + "@example.test";
        MvcResult registration = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new RegisterBody(
                                email, TEST_PASSWORD, "Test", "Customer", uniquePhone()))))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode challenge = objectMapper.readTree(registration.getResponse().getContentAsByteArray());
        String challengeId = challenge.path("challengeId").asText();
        String otp = challenge.path("developmentOtp").asText();

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new LoginBody(email, TEST_PASSWORD))))
                .andExpect(status().isUnauthorized());

        MvcResult verification = mockMvc.perform(post("/api/v1/auth/verify-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new OtpBody(challengeId, otp))))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode tokens = objectMapper.readTree(verification.getResponse().getContentAsByteArray());
        return new TestUser(
                email,
                tokens.path("accessToken").asText(),
                tokens.path("refreshToken").asText());
    }

    private TestUser loginSeed(String email) throws Exception {
        MvcResult login = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new LoginBody(email, "test-only-seed-password"))))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode tokens = objectMapper.readTree(login.getResponse().getContentAsByteArray());
        return new TestUser(email, tokens.path("accessToken").asText(), tokens.path("refreshToken").asText());
    }

    private int concurrentBooking(
            String accessToken,
            BookingBody request,
            CountDownLatch ready,
            CountDownLatch start) throws Exception {
        ready.countDown();
        if (!start.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Concurrent booking test did not start");
        }
        return mockMvc.perform(post("/api/v1/bookings")
                        .header("Authorization", bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(request)))
                .andReturn()
                .getResponse()
                .getStatus();
    }

    private String json(Object body) throws Exception {
        return objectMapper.writeValueAsString(body);
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private String uniquePhone() {
        return "+1555" + String.format("%07d", Math.abs(UUID.randomUUID().hashCode()) % 10_000_000);
    }

    private record TestUser(String email, String accessToken, String refreshToken) {
    }

    private record RegisterBody(String email, String password, String firstName, String lastName, String phoneNumber) {
    }

    private record LoginBody(String email, String password) {
    }

    private record OtpBody(String challengeId, String code) {
    }

    private record RefreshBody(String refreshToken) {
    }

    private record PhoneBody(String phoneNumber) {
    }

    private record RechargeBody(long mobileNumberId, long planId, String idempotencyKey) {
    }

    private record ConfirmBody(String callbackId, String outcome) {
    }

    private record RefundBody(BigDecimal amount, String reason, String idempotencyKey) {
    }

    private record PurchaseBody(long planId, boolean autoRenew, String idempotencyKey) {
    }

    private record IdempotencyBody(String idempotencyKey) {
    }

    private record BookingBody(long catalogItemId, String seatOrSlot, String idempotencyKey) {
    }

    private record SupportCreateBody(String subject, String description, String category, String priority) {
    }

    private record SupportCommentBody(String body, boolean internal) {
    }

    private record TicketStatusBody(String status) {
    }

    private record OfferBody(
            String offerCode, String name, BigDecimal discountPercent,
            LocalDateTime startsAt, LocalDateTime endsAt, boolean active) {
    }

    private record CouponBody(String couponCode, int usageLimit) {
    }

    private record RedeemBody(String code) {
    }

    private record StreamContentBody(
            long categoryId,
            String contentCode,
            String title,
            String description,
            String contentType,
            String rating,
            int durationMinutes,
            String playbackUrl,
            boolean active) {
    }
}
