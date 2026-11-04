package com.connectone.common.cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Invalidates the public plan catalogue AFTER the admin's transaction commits (evicting earlier would let a
 * concurrent reader re-cache the old rows). Uses Cache.invalidate(): unlike clear(), it is immediate for Redis.
 * Eviction failures are logged, never thrown: the entry then simply expires with its 5 minute TTL.
 */
@Component
public class PlanCatalogueCache {

    private static final Logger LOGGER = LoggerFactory.getLogger(PlanCatalogueCache.class);
    private final CacheManager cacheManager;

    public PlanCatalogueCache(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    public void evictRechargePlansAfterCommit() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    evictNow();
                }
            });
        } else {
            evictNow();
        }
    }

    void evictNow() {
        try {
            Cache cache = cacheManager.getCache(CacheConfiguration.RECHARGE_PLANS);
            if (cache != null) {
                cache.invalidate();
            }
        } catch (RuntimeException e) {
            LOGGER.warn("event=cache_error op=invalidate cache={} cause={}", CacheConfiguration.RECHARGE_PLANS, e.getClass().getSimpleName());
        }
    }
}
