package com.connectone.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

/**
 * Real Kafka (in-JVM KRaft broker, no Docker) + real PostgreSQL: publishes events exactly as core-service's relay does and
 * checks the consumer stores them, ignores foreign events, and parks a poison message on the dead-letter topic.
 */
@SpringBootTest(properties = {
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "spring.kafka.listener.auto-startup=true",
        "connectone.events.topic=test.events.v1"})
@EmbeddedKafka(partitions = 1, topics = {"test.events.v1", "test.events.v1.DLT"})
@ActiveProfiles("test")
@Import(TestDatabaseConfiguration.class)
@DirtiesContext
class KafkaRoundTripTests {

    @Autowired EmbeddedKafkaBroker broker;
    @Autowired JdbcTemplate jdbc;

    private KafkaTemplate<String, String> producer() {
        Map<String, Object> props = org.springframework.kafka.test.utils.KafkaTestUtils.producerProps(broker);
        return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(props, new org.apache.kafka.common.serialization.StringSerializer(),
                new org.apache.kafka.common.serialization.StringSerializer()));
    }

    private ProducerRecord<String, String> record(String key, String eventType, String json) {
        ProducerRecord<String, String> r = new ProducerRecord<>("test.events.v1", key, json);
        r.headers().add("event-type", eventType.getBytes());
        return r;
    }

    private static String json(long customerId, String idempotencyKey) {
        return "{\"eventId\":\"" + UUID.randomUUID() + "\",\"schemaVersion\":1,\"customerId\":" + customerId + ",\"userId\":" + (customerId + 1000)
                + ",\"type\":\"PAYMENT_SUCCEEDED\",\"subject\":\"s\",\"message\":\"m\",\"idempotencyKey\":\"" + idempotencyKey + "\"}";
    }

    @Test
    void storesEventsDeduplicatesRedeliveriesIgnoresForeignTypesAndParksPoisonMessages() throws Exception {
        KafkaTemplate<String, String> kafka = producer();
        long customerId = 9100 + (System.nanoTime() % 800);
        String key = "kafka-" + UUID.randomUUID();
        String body = json(customerId, key);

        kafka.send(record(Long.toString(customerId), "notification.requested", body)).get();
        kafka.send(record(Long.toString(customerId), "notification.requested", body)).get();          // at-least-once redelivery
        kafka.send(record(Long.toString(customerId), "some.other.event", "{\"whatever\":true}")).get();  // not ours: must be ignored
        kafka.send(record(Long.toString(customerId), "notification.requested", "{this is not json")).get(); // poison

        await().atMost(Duration.ofSeconds(40)).untilAsserted(() ->
                assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM notification_record WHERE customer_id = ?", Integer.class, customerId)).isEqualTo(1));

        // the poison record lands on the dead-letter topic instead of blocking the partition
        try (var consumer = new org.apache.kafka.clients.consumer.KafkaConsumer<String, String>(
                org.springframework.kafka.test.utils.KafkaTestUtils.consumerProps(broker, "dlt-check-" + UUID.randomUUID(), true),
                new org.apache.kafka.common.serialization.StringDeserializer(), new org.apache.kafka.common.serialization.StringDeserializer())) {
            consumer.subscribe(java.util.List.of("test.events.v1.DLT"));
            await().atMost(Duration.ofSeconds(40)).untilAsserted(() -> {
                var records = consumer.poll(Duration.ofMillis(500));
                assertThat(records.count()).isGreaterThanOrEqualTo(1);
                assertThat(records.iterator().next().value()).contains("this is not json");
            });
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM notification_record WHERE customer_id = ?", Integer.class, customerId)).isEqualTo(1);
    }
}
