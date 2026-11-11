package com.connectone.common.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import com.connectone.TestDatabaseConfiguration;
import com.connectone.notification.service.NotificationService;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

/** Outbox guarantees on PostgreSQL: transactional write, at-least-once relay, Kafka outage tolerance. */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestDatabaseConfiguration.class)
class OutboxTests {

    @Autowired JdbcTemplate jdbc;
    @Autowired TransactionTemplate tx;
    @Autowired NotificationService notifications;

    private long customer() {
        String tag = UUID.randomUUID().toString().substring(0, 8);
        long user = jdbc.queryForObject("INSERT INTO app_user (email, password_hash) VALUES (?, 'x') RETURNING id", Long.class, "ob-" + tag + "@t.test");
        return jdbc.queryForObject("INSERT INTO customer_profile (user_id, first_name, last_name) VALUES (?, 'O', 'B') RETURNING id", Long.class, user);
    }

    private OutboxRelay relay(KafkaTemplate<String, String> kafka) {
        return new OutboxRelay(jdbc, tx, kafka, new SimpleMeterRegistry(), "test.topic");
    }

    @SuppressWarnings("unchecked")
    private KafkaTemplate<String, String> kafkaThatAcknowledges() {
        KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
        when(kafka.send(any(ProducerRecord.class))).thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));
        return kafka;
    }

    @Test
    void eventIsWrittenInTheCallersTransactionAndRolledBackWithIt() {
        long customerId = customer();
        String key = "tx-" + UUID.randomUUID();

        assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
            notifications.enqueue(customerId, "TEST_EVENT", "s", "m", key);
            throw new IllegalStateException("business step failed after the event was recorded");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM outbox_event WHERE payload->>'idempotencyKey' = ?", Integer.class, key)).isZero();

        tx.executeWithoutResult(status -> notifications.enqueue(customerId, "TEST_EVENT", "s", "m", key));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM outbox_event WHERE payload->>'idempotencyKey' = ?", Integer.class, key)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT payload->>'customerId' FROM outbox_event WHERE payload->>'idempotencyKey' = ?", String.class, key))
                .isEqualTo(Long.toString(customerId));
    }

    @Test
    void relayPublishesInOrderWithTheCustomerAsKeyAndMarksRowsPublished() {
        long customerId = customer();
        String key = "relay-" + UUID.randomUUID();
        tx.executeWithoutResult(status -> notifications.enqueue(customerId, "RELAY_TEST", "s", "m", key));
        KafkaTemplate<String, String> kafka = kafkaThatAcknowledges();

        relay(kafka).relay();

        org.mockito.ArgumentCaptor<ProducerRecord<String, String>> sent = org.mockito.ArgumentCaptor.forClass(ProducerRecord.class);
        org.mockito.Mockito.verify(kafka, org.mockito.Mockito.atLeastOnce()).send(sent.capture());
        ProducerRecord<String, String> record = sent.getAllValues().stream().filter(r -> r.value().contains(key)).findFirst().orElseThrow();
        assertThat(record.topic()).isEqualTo("test.topic");
        assertThat(record.key()).isEqualTo(Long.toString(customerId));
        assertThat(new String(record.headers().lastHeader("event-type").value())).isEqualTo("notification.requested");
        assertThat(jdbc.queryForObject("SELECT published_at IS NOT NULL FROM outbox_event WHERE payload->>'idempotencyKey' = ?", Boolean.class, key)).isTrue();
    }

    @Test
    @SuppressWarnings("unchecked")
    void kafkaOutageLeavesRowsUnpublishedAndTheyAreSentAfterRecovery() {
        long customerId = customer();
        String key = "outage-" + UUID.randomUUID();
        tx.executeWithoutResult(status -> notifications.enqueue(customerId, "OUTAGE_TEST", "s", "m", key));
        KafkaTemplate<String, String> down = mock(KafkaTemplate.class);
        when(down.send(any(ProducerRecord.class))).thenReturn(CompletableFuture.failedFuture(new IllegalStateException("broker unavailable")));

        relay(down).relay();   // must not throw: the API must not depend on Kafka being up
        assertThat(jdbc.queryForObject("SELECT published_at IS NULL FROM outbox_event WHERE payload->>'idempotencyKey' = ?", Boolean.class, key)).isTrue();

        relay(kafkaThatAcknowledges()).relay();   // broker is back
        assertThat(jdbc.queryForObject("SELECT published_at IS NOT NULL FROM outbox_event WHERE payload->>'idempotencyKey' = ?", Boolean.class, key)).isTrue();
    }
}
