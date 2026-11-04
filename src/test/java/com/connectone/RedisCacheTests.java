package com.connectone;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.net.InetSocketAddress;
import java.net.Socket;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.connectone.common.cache.CacheConfiguration;
import com.connectone.telecom.service.RechargePlanService;

/**
 * Redis behaviour for the recharge-plan catalogue cache. Needs the connectone-redis container (port 6380) for the
 * "available" test (skipped otherwise); the "unavailable" test needs nothing and proves the API fails open.
 */
@Tag("redis")
class RedisCacheTests {

    static boolean redisReachable(int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("localhost", port), 300);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Nested
    @SpringBootTest(properties = {"spring.cache.type=redis", "spring.data.redis.port=6380", "spring.data.redis.database=15", "spring.cache.redis.key-prefix=connectone-test:"})
    @ActiveProfiles("test")
    @Import(TestDatabaseConfiguration.class)
    class WhenRedisIsAvailable {

        @Autowired RechargePlanService plans;
        @Autowired JdbcTemplate jdbc;
        @Autowired CacheManager cacheManager;
        @Autowired StringRedisTemplate redis;
        @Autowired com.connectone.admin.service.AdminService admin;

        @Test
        void servesTheCatalogueFromRedisUntilItIsEvicted() {
            assumeTrue(redisReachable(6380), "connectone-redis is not running on 6380");
            cacheManager.getCache(CacheConfiguration.RECHARGE_PLANS).invalidate();

            var first = plans.searchAvailable(null, null, null, null);
            assertThat(redis.keys("connectone-test:recharge-plans::*")).isNotEmpty();

            // change the database behind the cache's back: the cached answer must still be returned
            assertThat(jdbc.update("UPDATE recharge_plan SET name = 'CHANGED-BEHIND-CACHE' WHERE plan_code = 'RCH-DAILY-1GB'")).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT name FROM recharge_plan WHERE plan_code = 'RCH-DAILY-1GB'", String.class)).isEqualTo("CHANGED-BEHIND-CACHE");
            var cached = plans.searchAvailable(null, null, null, null);
            assertThat(cached).extracting("name").doesNotContain("CHANGED-BEHIND-CACHE");
            assertThat(cached).hasSameSizeAs(first);

            // after eviction the fresh value is read and re-cached
            cacheManager.getCache(CacheConfiguration.RECHARGE_PLANS).invalidate();
            assertThat(redis.keys("connectone-test:recharge-plans::*")).as("keys after clear").isEmpty();
            assertThat(plans.searchAvailable(null, null, null, null)).extracting("name").contains("CHANGED-BEHIND-CACHE");
            jdbc.update("UPDATE recharge_plan SET name = 'Daily 1 GB' WHERE plan_code = 'RCH-DAILY-1GB'");

            // an admin plan edit must evict the cached catalogue (after commit), not wait for the TTL
            plans.searchAvailable(null, null, null, null);
            assertThat(redis.keys("connectone-test:recharge-plans::*")).isNotEmpty();
            long actor = jdbc.queryForObject("SELECT id FROM app_user WHERE email = 'seed-admin@connectone.local'", Long.class);
            long planId = jdbc.queryForObject("SELECT id FROM recharge_plan WHERE plan_code = 'RCH-DAILY-1GB'", Long.class);
            admin.updateRechargePlan(actor, planId, new com.connectone.admin.dto.AdminDtos.RechargePlanRequest(
                    "RCH-DAILY-1GB", "Daily 1 GB", new java.math.BigDecimal("31.00"), 1, 1024L, 100, true));
            assertThat(redis.keys("connectone-test:recharge-plans::*")).as("evicted by admin edit").isEmpty();
            assertThat(plans.searchAvailable(null, null, null, null)).extracting("price").contains(new java.math.BigDecimal("31.00"));
            cacheManager.getCache(CacheConfiguration.RECHARGE_PLANS).invalidate();
        }
    }

    @Nested
    @SpringBootTest(properties = {"spring.cache.type=redis", "spring.data.redis.port=6399", "spring.data.redis.database=15", "spring.cache.redis.key-prefix=connectone-test:"})
    @ActiveProfiles("test")
    @Import(TestDatabaseConfiguration.class)
    class WhenRedisIsDown {

        @Autowired RechargePlanService plans;

        @Test
        void theApiFailsOpenAndServesFromPostgres() {
            assumeTrue(!redisReachable(6399), "something is listening on the 'dead' Redis port");
            assertThat(plans.searchAvailable(null, null, null, null)).isNotEmpty();
            assertThat(plans.searchAvailable("monthly", null, null, null)).isNotEmpty();
        }
    }
}
