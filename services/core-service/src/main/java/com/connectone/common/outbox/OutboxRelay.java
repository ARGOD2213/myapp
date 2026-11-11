package com.connectone.common.outbox;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import io.micrometer.core.instrument.MeterRegistry;

/**
 * Publishes unpublished outbox rows to Kafka, oldest first, key = aggregate id (so all events of one customer stay
 * ordered on one partition). Delivery is AT-LEAST-ONCE: if the process dies after Kafka acknowledged a message but
 * before the row is marked published, the row is sent again — consumers must be idempotent (notification-service is).
 * Rows are locked with SKIP LOCKED so several core-service instances never publish the same row concurrently.
 * If Kafka is down the rows simply accumulate; the API keeps working.
 */
@Component
@ConditionalOnProperty(name = "connectone.outbox.relay.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxRelay {

    private static final Logger LOGGER = LoggerFactory.getLogger(OutboxRelay.class);

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate tx;
    private final KafkaTemplate<String, String> kafka;
    private final String topic;
    private final AtomicLong pending = new AtomicLong();
    private volatile boolean degraded;

    public OutboxRelay(JdbcTemplate jdbcTemplate, TransactionTemplate tx, KafkaTemplate<String, String> kafka,
            MeterRegistry meters, @Value("${connectone.events.topic:connectone.events.v1}") String topic) {
        this.jdbcTemplate = jdbcTemplate;
        this.tx = tx;
        this.kafka = kafka;
        this.topic = topic;
        meters.gauge("connectone.outbox.pending", pending);
    }

    @Scheduled(fixedDelayString = "${connectone.outbox.relay.interval-ms:500}")
    public void relay() {
        try {
            Integer sent = tx.execute(status -> publishBatch());
            degraded = false;
            if (sent != null && sent > 0) {
                LOGGER.info("event=outbox_published count={}", sent);
            }
        } catch (RuntimeException e) {
            if (!degraded) { // log the transition once, not every 500 ms
                LOGGER.warn("event=outbox_relay_degraded cause={} (rows stay in the outbox and are retried)", e.getClass().getSimpleName());
            }
            degraded = true;
        }
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM outbox_event WHERE published_at IS NULL", Long.class);
        pending.set(count == null ? 0 : count);
    }

    int publishBatch() {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id, aggregate_id, event_type, payload::text AS payload, correlation_id FROM outbox_event "
                        + "WHERE published_at IS NULL ORDER BY id LIMIT 100 FOR UPDATE SKIP LOCKED");
        for (Map<String, Object> row : rows) {
            ProducerRecord<String, String> record = new ProducerRecord<>(topic, (String) row.get("aggregate_id"), (String) row.get("payload"));
            record.headers().add("event-type", ((String) row.get("event_type")).getBytes(StandardCharsets.UTF_8));
            if (row.get("correlation_id") != null) {
                record.headers().add("correlation-id", ((String) row.get("correlation_id")).getBytes(StandardCharsets.UTF_8));
            }
            try {
                kafka.send(record).get(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            } catch (Exception e) {
                throw new IllegalStateException("Kafka publish failed", e); // rolls back: earlier rows of this batch are re-sent (at-least-once)
            }
            jdbcTemplate.update("UPDATE outbox_event SET published_at = CURRENT_TIMESTAMP WHERE id = ?", row.get("id"));
        }
        return rows.size();
    }
}
