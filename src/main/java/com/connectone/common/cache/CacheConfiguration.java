package com.connectone.common.cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.cache.Cache;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.stereotype.Component;

/**
 * Caching is optional (spring.cache.type=redis switches it on) and FAIL-OPEN: if Redis is unreachable every cache
 * error is logged and swallowed so the request is served from PostgreSQL. A cache outage must never be an API outage.
 * Known limitation (see docs/KNOWN_LIMITATIONS.md): there is no circuit breaker, so each call still pays the
 * Redis connect timeout while Redis is down.
 */
@Configuration
@EnableCaching
public class CacheConfiguration implements CachingConfigurer {

    public static final String RECHARGE_PLANS = "recharge-plans";
    private static final Logger LOGGER = LoggerFactory.getLogger(CacheConfiguration.class);

    @Override
    public CacheErrorHandler errorHandler() {
        return new CacheErrorHandler() {
            @Override
            public void handleCacheGetError(RuntimeException e, Cache cache, Object key) {
                LOGGER.warn("event=cache_error op=get cache={} cause={}", cache.getName(), e.getClass().getSimpleName());
            }

            @Override
            public void handleCachePutError(RuntimeException e, Cache cache, Object key, Object value) {
                LOGGER.warn("event=cache_error op=put cache={} cause={}", cache.getName(), e.getClass().getSimpleName());
            }

            @Override
            public void handleCacheEvictError(RuntimeException e, Cache cache, Object key) {
                LOGGER.warn("event=cache_error op=evict cache={} cause={}", cache.getName(), e.getClass().getSimpleName());
            }

            @Override
            public void handleCacheClearError(RuntimeException e, Cache cache) {
                LOGGER.warn("event=cache_error op=clear cache={} cause={}", cache.getName(), e.getClass().getSimpleName());
            }
        };
    }

    /** Reports the cache mode; always UP so a Redis outage never flips readiness (the API degrades, it does not fail). */
    @Component("cache")
    static class CacheHealth implements HealthIndicator {

        private final String mode;
        private final ObjectProvider<RedisConnectionFactory> redis;

        CacheHealth(@Value("${spring.cache.type:none}") String mode, ObjectProvider<RedisConnectionFactory> redis) {
            this.mode = mode;
            this.redis = redis;
        }

        @Override
        public Health health() {
            Health.Builder builder = Health.up().withDetail("mode", mode);
            RedisConnectionFactory factory = redis.getIfAvailable();
            if ("redis".equals(mode) && factory != null) {
                try (RedisConnection connection = factory.getConnection()) {
                    connection.ping();
                    builder.withDetail("redis", "reachable");
                } catch (RuntimeException e) {
                    builder.withDetail("redis", "unreachable: serving from PostgreSQL without cache");
                }
            }
            return builder.build();
        }
    }
}
