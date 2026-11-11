package com.connectone.common.outbox;

import java.util.Map;

import org.slf4j.MDC;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.connectone.common.web.CorrelationIdFilter;

import tools.jackson.databind.ObjectMapper;

/**
 * Transactional outbox: records "something happened" in the same database transaction as the business change.
 * Either both commit or neither does, so an event is never lost and never published for a rolled-back change.
 * {@link OutboxRelay} delivers the rows to Kafka afterwards.
 */
@Service
public class OutboxService {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public OutboxService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void publish(String aggregateType, String aggregateId, String eventType, Map<String, Object> payload) {
        jdbcTemplate.update(
                "INSERT INTO outbox_event (aggregate_type, aggregate_id, event_type, payload, correlation_id) "
                        + "VALUES (?, ?, ?, ?::jsonb, ?)",
                aggregateType, aggregateId, eventType, objectMapper.writeValueAsString(payload),
                MDC.get(CorrelationIdFilter.MDC_KEY));
    }
}
