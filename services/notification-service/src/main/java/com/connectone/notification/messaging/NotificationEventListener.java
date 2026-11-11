package com.connectone.notification.messaging;

import java.nio.charset.StandardCharsets;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.connectone.notification.service.NotificationRequested;
import com.connectone.notification.service.NotificationStore;

import io.micrometer.core.instrument.MeterRegistry;
import tools.jackson.databind.ObjectMapper;

/**
 * Consumes domain events. Offsets are committed only after {@link #on} returns, so a crash re-delivers the record
 * (at-least-once) and {@link NotificationStore#accept} makes the repeat harmless. A record that cannot be processed is
 * retried with back-off and then parked on the dead-letter topic by {@link KafkaConsumerConfiguration}.
 */
@Component
public class NotificationEventListener {

    public static final String EVENT_TYPE = "notification.requested";
    private static final Logger LOGGER = LoggerFactory.getLogger(NotificationEventListener.class);

    private final NotificationStore store;
    private final ObjectMapper mapper;
    private final MeterRegistry meters;

    public NotificationEventListener(NotificationStore store, ObjectMapper mapper, MeterRegistry meters) {
        this.store = store;
        this.mapper = mapper;
        this.meters = meters;
    }

    @KafkaListener(topics = "${connectone.events.topic}")
    public void on(ConsumerRecord<String, String> record) {
        String eventType = header(record, "event-type");
        String correlationId = header(record, "correlation-id");
        if (correlationId != null) {
            MDC.put("correlationId", correlationId);
        }
        try {
            if (!EVENT_TYPE.equals(eventType)) {
                return; // other event types on the shared topic are not ours: ignore, do not fail
            }
            NotificationRequested event = mapper.readValue(record.value(), NotificationRequested.class);
            boolean created = store.accept(event);
            meters.counter("connectone.notification.events", "result", created ? "stored" : "duplicate").increment();
            LOGGER.info("event=notification_consumed partition={} offset={} result={}", record.partition(), record.offset(), created ? "stored" : "duplicate");
        } finally {
            MDC.remove("correlationId");
        }
    }

    private static String header(ConsumerRecord<String, String> record, String name) {
        Header h = record.headers().lastHeader(name);
        return h == null ? null : new String(h.value(), StandardCharsets.UTF_8);
    }
}
