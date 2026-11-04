package com.connectone.support.service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.connectone.common.audit.AuditService;
import com.connectone.common.database.JdbcRowSupport;
import com.connectone.common.database.GeneratedKeySupport;
import com.connectone.common.exception.ApiException;
import com.connectone.notification.service.NotificationService;
import com.connectone.support.dto.SupportDtos.AssignmentRequest;
import com.connectone.support.dto.SupportDtos.CommentRequest;
import com.connectone.support.dto.SupportDtos.CreateTicketRequest;

@Service
public class SupportService {

    private final JdbcTemplate jdbcTemplate;
    private final AuditService auditService;
    private final NotificationService notifications;

    public SupportService(JdbcTemplate jdbcTemplate, AuditService auditService, NotificationService notifications) {
        this.jdbcTemplate = jdbcTemplate;
        this.auditService = auditService;
        this.notifications = notifications;
    }

    @Transactional
    public Map<String, Object> create(long userId, CreateTicketRequest request) {
        long customerId = customerId(userId);
        if (!List.of("LOW", "NORMAL", "HIGH", "URGENT").contains(request.priority())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Unsupported ticket priority");
        }
        String code = "TKT-" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        long id = GeneratedKeySupport.insert(jdbcTemplate,
                "INSERT INTO support_ticket "
                        + "(ticket_code, customer_id, subject, description, category, priority, status) "
                        + "VALUES (?, ?, ?, ?, ?, ?, 'OPEN')",
                code, customerId, request.subject().strip(), request.description().strip(),
                request.category().strip().toUpperCase(java.util.Locale.ROOT), request.priority());
        historyEvent(id, userId, "CREATED", "Ticket created");
        notifications.enqueue(customerId, "SUPPORT_TICKET_CREATED", "Support request received",
                "Your support ticket " + code + " was created.", "ticket-created-" + id);
        return details(userId, id, false);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> customerTickets(long userId) {
        return JdbcRowSupport.camelCaseRows(jdbcTemplate.queryForList(
                "SELECT id, ticket_code, subject, category, priority, status, assigned_user_id, created_at, updated_at "
                        + "FROM support_ticket WHERE customer_id = ? ORDER BY created_at DESC, id DESC",
                customerId(userId)));
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> search(
            String status, String priority, String category, int page, int size, String sort) {
        String sortColumn = switch (sort == null ? "createdAt" : sort) {
            case "priority" -> "priority";
            case "updatedAt" -> "updated_at";
            case "createdAt" -> "created_at";
            default -> throw new ApiException(HttpStatus.BAD_REQUEST, "Unsupported ticket sort field");
        };
        int boundedPage = Math.max(page, 0);
        int boundedSize = Math.min(Math.max(size, 1), 100);
        return JdbcRowSupport.camelCaseRows(jdbcTemplate.queryForList(
                "SELECT id, ticket_code, customer_id, assigned_user_id, subject, category, priority, status, "
                        + "created_at, updated_at FROM support_ticket "
                        + "WHERE (CAST(? AS VARCHAR) IS NULL OR status = ?) AND (CAST(? AS VARCHAR) IS NULL OR priority = ?) "
                        + "AND (CAST(? AS VARCHAR) IS NULL OR category = ?) ORDER BY " + sortColumn
                        + " DESC, id DESC LIMIT ? OFFSET ?",
                status, status, priority, priority, category, category, boundedSize,
                (long) boundedPage * boundedSize));
    }

    @Transactional(readOnly = true)
    public Map<String, Object> details(long userId, long ticketId, boolean agent) {
        Map<String, Object> ticket = JdbcRowSupport.camelCaseRow(findTicket(userId, ticketId, agent, false));
        List<Map<String, Object>> comments = JdbcRowSupport.camelCaseRows(jdbcTemplate.queryForList(
                "SELECT id, author_user_id, body, is_internal, created_at FROM ticket_comment "
                        + "WHERE ticket_id = ? AND (? = TRUE OR is_internal = FALSE) ORDER BY created_at, id",
                ticketId, agent));
        return Map.of("ticket", ticket, "comments", comments);
    }

    @Transactional
    public void assign(long actorId, long ticketId, AssignmentRequest request) {
        Integer validAgent = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user_role ur JOIN app_role r ON r.id = ur.role_id "
                        + "JOIN app_user u ON u.id = ur.user_id WHERE ur.user_id = ? "
                        + "AND r.role_code IN ('SUPPORT_AGENT', 'ADMIN') AND u.account_status = 'ACTIVE'",
                Integer.class, request.agentUserId());
        if (validAgent == null || validAgent == 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Assigned user is not an active support agent");
        }
        jdbcTemplate.update(
                "UPDATE support_ticket SET assigned_user_id = ?, updated_at = CURRENT_TIMESTAMP, "
                        + "version = version + 1 WHERE id = ?",
                request.agentUserId(), ticketId);
        if (ticketExists(ticketId) == 0) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Support ticket was not found");
        }
        historyEvent(ticketId, actorId, "ASSIGNED", "Ticket assigned to support agent");
        auditService.record(actorId, "TICKET_ASSIGNED", "SUPPORT_TICKET", ticketId);
    }

    @Transactional
    public void transition(long actorId, long ticketId, String nextStatus) {
        Map<String, Object> ticket = findTicket(actorId, ticketId, true, true);
        String current = (String) ticket.get("status");
        boolean allowed = switch (current) {
            case "OPEN" -> List.of("IN_PROGRESS", "WAITING").contains(nextStatus);
            case "IN_PROGRESS" -> List.of("WAITING", "RESOLVED").contains(nextStatus);
            case "WAITING" -> List.of("IN_PROGRESS", "RESOLVED").contains(nextStatus);
            case "RESOLVED" -> List.of("IN_PROGRESS", "CLOSED").contains(nextStatus);
            default -> false;
        };
        if (!allowed) {
            throw new ApiException(HttpStatus.CONFLICT, "Ticket cannot transition from " + current + " to " + nextStatus);
        }
        jdbcTemplate.update(
                "UPDATE support_ticket SET status = ?, updated_at = CURRENT_TIMESTAMP, "
                        + "closed_at = CASE WHEN ? = 'CLOSED' THEN CURRENT_TIMESTAMP ELSE NULL END, "
                        + "version = version + 1 WHERE id = ?",
                nextStatus, nextStatus, ticketId);
        historyEvent(ticketId, actorId, "STATUS_CHANGED", current + " -> " + nextStatus);
        auditService.record(actorId, "TICKET_STATUS_CHANGED", "SUPPORT_TICKET", ticketId);
        if ("RESOLVED".equals(nextStatus) || "CLOSED".equals(nextStatus)) {
            notifications.enqueue(((Number) ticket.get("customer_id")).longValue(), "SUPPORT_TICKET_UPDATED",
                    "Support request updated", "Your support ticket status is " + nextStatus + ".",
                    "ticket-" + ticketId + "-" + nextStatus + "-" + ((Number) ticket.get("version")).longValue());
        }
    }

    @Transactional
    public void comment(long authorId, long ticketId, CommentRequest request, boolean agent) {
        Map<String, Object> ticket = findTicket(authorId, ticketId, agent, true);
        if ("CLOSED".equals(ticket.get("status"))) {
            throw new ApiException(HttpStatus.CONFLICT, "Closed tickets cannot receive comments");
        }
        boolean internal = agent && request.internal();
        jdbcTemplate.update(
                "INSERT INTO ticket_comment (ticket_id, author_user_id, body, is_internal) VALUES (?, ?, ?, ?)",
                ticketId, authorId, request.body().strip(), internal);
        historyEvent(ticketId, authorId, "COMMENT_ADDED", internal ? "Internal agent comment" : "Visible reply");
        if (!internal) {
            notifications.enqueue(((Number) ticket.get("customer_id")).longValue(), "SUPPORT_TICKET_REPLY",
                    "New support reply", "A reply was added to support ticket " + ticket.get("ticket_code") + ".",
                    "ticket-reply-" + ticketId + "-" + System.nanoTime());
        }
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> history(long userId, long ticketId, boolean agent) {
        findTicket(userId, ticketId, agent, false);
        return JdbcRowSupport.camelCaseRows(jdbcTemplate.queryForList(
                "SELECT event_type, details, created_at FROM ticket_history WHERE ticket_id = ? "
                        + "ORDER BY created_at, id", ticketId));
    }

    private Map<String, Object> findTicket(long userId, long ticketId, boolean agent, boolean lock) {
        String sql = "SELECT t.id, t.ticket_code, t.customer_id, t.assigned_user_id, t.subject, t.description, "
                + "t.category, t.priority, t.status, t.version FROM support_ticket t WHERE t.id = ?"
                + (agent ? "" : " AND t.customer_id = (SELECT id FROM customer_profile WHERE user_id = ?)")
                + (lock ? " FOR UPDATE" : "");
        try {
            return agent
                    ? jdbcTemplate.queryForMap(sql, ticketId)
                    : jdbcTemplate.queryForMap(sql, ticketId, userId);
        } catch (org.springframework.dao.EmptyResultDataAccessException exception) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Support ticket was not found");
        }
    }

    private void historyEvent(long ticketId, Long actorId, String type, String details) {
        jdbcTemplate.update(
                "INSERT INTO ticket_history (ticket_id, actor_user_id, event_type, details) VALUES (?, ?, ?, ?)",
                ticketId, actorId, type, details);
    }

    private int ticketExists(long ticketId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM support_ticket WHERE id = ?", Integer.class, ticketId);
        return count == null ? 0 : count;
    }

    private long customerId(long userId) {
        Long id = jdbcTemplate.query(
                "SELECT id FROM customer_profile WHERE user_id = ? AND account_status = 'ACTIVE'",
                result -> result.next() ? result.getLong(1) : null, userId);
        if (id == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Customer profile was not found");
        }
        return id;
    }
}
