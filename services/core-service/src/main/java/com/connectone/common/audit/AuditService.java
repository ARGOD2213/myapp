package com.connectone.common.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.connectone.common.web.CorrelationIdFilter;

@Service
public class AuditService {

    private static final Logger BUSINESS_LOG = LoggerFactory.getLogger("connectone.audit");
    private final JdbcTemplate jdbcTemplate;

    public AuditService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void record(long actorUserId, String eventType, String entityType, Object entityId) {
        record(actorUserId, eventType, entityType, entityId, null);
    }

    public void record(long actorUserId, String eventType, String entityType, Object entityId, String details) {
        String entity = entityId == null ? null : entityId.toString();
        jdbcTemplate.update(
                "INSERT INTO audit_event (actor_user_id, event_type, entity_type, entity_id, correlation_id, details) "
                        + "VALUES (?, ?, ?, ?, ?, ?)",
                actorUserId, eventType, entityType, entity, MDC.get(CorrelationIdFilter.MDC_KEY), details);
        BUSINESS_LOG.info("event={} entityType={} entityId={} actor={}", eventType, entityType, entity, actorUserId);
    }
}
