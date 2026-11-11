package com.connectone.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.connectone.notification.service.NotificationRequested;
import com.connectone.notification.service.NotificationStore;

import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestDatabaseConfiguration.class)
class NotificationServiceTests {

    static final String SECRET = "test-only-connectone-jwt-signing-secret-32-bytes-minimum";

    @Autowired NotificationStore store;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;

    static String token(long userId, List<String> roles, List<String> permissions, long expiresInSeconds) throws Exception {
        ObjectMapper m = new ObjectMapper();
        String header = b64(m.writeValueAsString(Map.of("alg", "HS256", "typ", "JWT")));
        long now = Instant.now().getEpochSecond();
        String payload = b64(m.writeValueAsString(Map.of("sub", Long.toString(userId), "email", "t@t.test", "roles", roles,
                "permissions", permissions, "jti", UUID.randomUUID().toString(), "iat", now, "exp", now + expiresInSeconds)));
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String signature = Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal((header + "." + payload).getBytes(StandardCharsets.UTF_8)));
        return "Bearer " + header + "." + payload + "." + signature;
    }

    private static String b64(String s) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(s.getBytes(StandardCharsets.UTF_8));
    }

    private NotificationRequested event(long customerId, long userId, String key) {
        return new NotificationRequested(UUID.randomUUID().toString(), 1, customerId, userId, "RECHARGE_SUCCEEDED", "Recharge successful", "Your recharge succeeded.", key);
    }

    // ------------------------------------------------------------------ the event contract (what core-service publishes)
    @Test
    void parsesTheExactEventCorePublishesAndToleratesNewFields() throws Exception {
        String json = """
                {"eventId":"8d0e1c34-0000-4000-8000-000000000001","schemaVersion":1,"occurredAt":"2026-11-11T10:00:00Z",
                 "customerId":42,"userId":1042,"type":"PAYMENT_SUCCEEDED","subject":"Payment successful","message":"Payment 7 succeeded.",
                 "idempotencyKey":"payment-7-SUCCEEDED","someFieldAddedInV2":{"x":1}}""";
        NotificationRequested e = mapper.readValue(json, NotificationRequested.class);
        assertThat(e.customerId()).isEqualTo(42);
        assertThat(e.userId()).isEqualTo(1042L);
        assertThat(e.idempotencyKey()).isEqualTo("payment-7-SUCCEEDED");
    }

    @Test
    void rejectsPayloadsMissingRequiredFields() {
        assertThatThrownBy(() -> mapper.readValue("{\"customerId\":1,\"type\":\"X\"}", NotificationRequested.class)).isInstanceOf(tools.jackson.core.JacksonException.class); // JacksonException is configured as non-retryable -> straight to the DLT
    }

    // ------------------------------------------------------------------ idempotent consumer
    @Test
    void redeliveringTheSameEventCreatesOneNotification() {
        long customerId = Math.abs(UUID.randomUUID().getMostSignificantBits() % 1_000_000);
        String key = "dup-" + UUID.randomUUID();
        assertThat(store.accept(event(customerId, customerId + 1000, key))).isTrue();
        assertThat(store.accept(event(customerId, customerId + 1000, key))).isFalse();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM notification_record WHERE customer_id = ?", Integer.class, customerId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT status FROM notification_record WHERE customer_id = ?", String.class, customerId)).isEqualTo("SENT");
    }

    @Test
    void concurrentDuplicateDeliveriesStillCreateOneRow() throws Exception {
        long customerId = Math.abs(UUID.randomUUID().getMostSignificantBits() % 1_000_000);
        String key = "race-" + UUID.randomUUID();
        ExecutorService pool = Executors.newFixedThreadPool(8);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Boolean>> results = new java.util.ArrayList<>();
        for (int i = 0; i < 8; i++) {
            results.add(pool.submit(() -> {
                go.await();
                return store.accept(event(customerId, customerId + 1000, key));
            }));
        }
        go.countDown();
        int stored = 0;
        for (Future<Boolean> f : results) {
            if (f.get()) {
                stored++;
            }
        }
        pool.shutdown();
        assertThat(stored).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM notification_record WHERE customer_id = ?", Integer.class, customerId)).isEqualTo(1);
    }

    // ------------------------------------------------------------------ REST + authorization
    @Test
    void customersSeeOnlyTheirOwnNotificationsAndCanMarkThemRead() throws Exception {
        long alice = 2001, bob = 2002;
        String keyA = "own-" + UUID.randomUUID();
        store.accept(event(alice, alice, keyA));
        store.accept(event(bob, bob, "own-" + UUID.randomUUID()));
        long id = jdbc.queryForObject("SELECT id FROM notification_record WHERE idempotency_key = ?", Long.class, keyA);

        mvc.perform(get("/api/v1/notifications/me").header("Authorization", token(alice, List.of("CUSTOMER"), List.of(), 300)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + id + ")].eventType").value("RECHARGE_SUCCEEDED"));
        mvc.perform(post("/api/v1/notifications/{id}/read", id).header("Authorization", token(bob, List.of("CUSTOMER"), List.of(), 300)))
                .andExpect(status().isNotFound());                    // someone else's notification looks like it does not exist
        mvc.perform(post("/api/v1/notifications/{id}/read", id).header("Authorization", token(alice, List.of("CUSTOMER"), List.of(), 300)))
                .andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("SELECT read_at IS NOT NULL FROM notification_record WHERE id = ?", Boolean.class, id)).isTrue();
    }

    @Test
    void rejectsMissingExpiredTamperedTokensAndWrongRoles() throws Exception {
        mvc.perform(get("/api/v1/notifications/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/notifications/me").header("Authorization", token(1, List.of("CUSTOMER"), List.of(), -10))).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/notifications/me").header("Authorization", token(1, List.of("CUSTOMER"), List.of(), 300) + "x")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/admin/notification-deliveries").header("Authorization", token(1, List.of("CUSTOMER"), List.of(), 300))).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/admin/notifications/retry").header("Authorization", token(9, List.of("SUPPORT_AGENT"), List.of("PAYMENT_MONITOR"), 300))).andExpect(status().isForbidden());
    }

    @Test
    void adminsSeeDeliveriesAndSummary() throws Exception {
        store.accept(event(3001, 3001, "adm-" + UUID.randomUUID()));
        String admin = token(1, List.of("ADMIN"), List.of("PAYMENT_MONITOR"), 300);
        mvc.perform(get("/api/v1/admin/notification-deliveries").param("size", "5").header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.page.size").value(5));
        mvc.perform(get("/api/v1/admin/notifications/summary").header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.key == 'SENT')].count").isNotEmpty());
    }
}
