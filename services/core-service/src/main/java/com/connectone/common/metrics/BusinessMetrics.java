package com.connectone.common.metrics;

import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.MeterRegistry;

/** Business counters visible at /actuator/metrics/connectone.* (admin only). */
@Component
public class BusinessMetrics {

    private final MeterRegistry registry;

    public BusinessMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void count(String name, String... tags) {
        registry.counter("connectone." + name, tags).increment();
    }
}
