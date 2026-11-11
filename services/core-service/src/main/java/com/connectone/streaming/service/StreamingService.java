package com.connectone.streaming.service;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.connectone.common.audit.AuditService;
import com.connectone.common.database.GeneratedKeySupport;
import com.connectone.common.database.JdbcRowSupport;
import com.connectone.common.exception.ApiException;
import com.connectone.streaming.dto.StreamingDtos.ContentRequest;

@Service
public class StreamingService {

    private final JdbcTemplate jdbcTemplate;
    private final AuditService auditService;

    public StreamingService(JdbcTemplate jdbcTemplate, AuditService auditService) {
        this.jdbcTemplate = jdbcTemplate;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> catalogue(String category, String query, String type) {
        return JdbcRowSupport.camelCaseRows(jdbcTemplate.queryForList(
                "SELECT c.id, c.content_code, c.title, c.description, c.content_type, c.rating, "
                        + "c.duration_minutes, c.category_id, g.name AS category, c.active "
                        + "FROM streaming_content c JOIN streaming_category g ON g.id = c.category_id "
                        + "WHERE c.active = TRUE AND g.active = TRUE "
                        + "AND (CAST(? AS VARCHAR) IS NULL OR g.category_code = ?) "
                        + "AND (CAST(? AS VARCHAR) IS NULL OR LOWER(c.title) LIKE LOWER(?)) "
                        + "AND (CAST(? AS VARCHAR) IS NULL OR c.content_type = ?) ORDER BY c.title LIMIT 200",
                category, category, query, like(query), type, type));
    }

    @Transactional(readOnly = true)
    public Map<String, Object> details(long contentId) {
        try {
            return JdbcRowSupport.camelCaseRow(jdbcTemplate.queryForMap(
                    "SELECT c.id, c.content_code, c.title, c.description, c.content_type, c.rating, "
                            + "c.duration_minutes, g.category_code, g.name AS category "
                            + "FROM streaming_content c JOIN streaming_category g ON g.id = c.category_id "
                            + "WHERE c.id = ? AND c.active = TRUE AND g.active = TRUE",
                    contentId));
        } catch (org.springframework.dao.EmptyResultDataAccessException exception) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Streaming content was not found");
        }
    }

    @Transactional
    public Map<String, Object> play(long userId, long contentId, int progressSeconds) {
        if (progressSeconds < 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Playback progress cannot be negative");
        }
        Map<String, Object> content;
        try {
            content = jdbcTemplate.queryForMap(
                    "SELECT c.id, c.title, c.category_id, c.playback_url, c.duration_minutes "
                            + "FROM streaming_content c "
                            + "JOIN streaming_category g ON g.id = c.category_id "
                            + "WHERE c.id = ? AND c.active = TRUE AND g.active = TRUE",
                    contentId);
        } catch (org.springframework.dao.EmptyResultDataAccessException exception) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Streaming content was not found");
        }
        if (progressSeconds > ((Number) content.get("duration_minutes")).intValue() * 60L) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Playback progress exceeds the content duration");
        }
        Long customerId = jdbcTemplate.query(
                "SELECT id FROM customer_profile WHERE user_id = ? AND account_status = 'ACTIVE'",
                result -> result.next() ? result.getLong(1) : null, userId);
        if (customerId == null) {
            throw new ApiException(HttpStatus.FORBIDDEN, "An active customer profile is required");
        }
        Integer entitled = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM customer_subscription s "
                        + "JOIN subscription_plan p ON p.id = s.plan_id "
                        + "JOIN subscription_content_entitlement e ON e.plan_id = p.id "
                        + "WHERE s.customer_id = ? AND e.category_id = ? AND s.status = 'ACTIVE' "
                        + "AND s.starts_at <= CURRENT_TIMESTAMP AND s.expires_at > CURRENT_TIMESTAMP "
                        + "AND EXISTS (SELECT 1 FROM payment_record pay WHERE pay.subscription_id = s.id "
                        + "AND pay.status = 'SUCCEEDED')",
                Integer.class, customerId, content.get("category_id"));
        if (entitled == null || entitled == 0) {
            throw new ApiException(HttpStatus.FORBIDDEN, "An active subscription with this content entitlement is required");
        }
        jdbcTemplate.update(
                "INSERT INTO streaming_watch_history (customer_id, content_id, progress_seconds) VALUES (?, ?, ?)",
                customerId, contentId, progressSeconds);
        return Map.of("contentId", contentId, "title", content.get("title"),
                "playbackUrl", content.get("playback_url"), "progressSeconds", progressSeconds);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> watchHistory(long userId) {
        Long customerId = jdbcTemplate.query(
                "SELECT id FROM customer_profile WHERE user_id = ? AND account_status = 'ACTIVE'",
                result -> result.next() ? result.getLong(1) : null, userId);
        if (customerId == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Customer profile was not found");
        }
        return JdbcRowSupport.camelCaseRows(jdbcTemplate.queryForList(
                "SELECT h.id, c.content_code, c.title, h.progress_seconds, h.watched_at "
                        + "FROM streaming_watch_history h JOIN streaming_content c ON c.id = h.content_id "
                        + "WHERE h.customer_id = ? ORDER BY h.watched_at DESC, h.id DESC LIMIT 200",
                customerId));
    }

    @Transactional
    public long createContent(long adminId, ContentRequest request) {
        if (!List.of("MOVIE", "SERIES", "CLIP").contains(request.contentType())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Unsupported streaming content type");
        }
        long id = GeneratedKeySupport.insert(jdbcTemplate,
                "INSERT INTO streaming_content "
                        + "(category_id, content_code, title, description, content_type, rating, "
                        + "duration_minutes, playback_url, active) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                request.categoryId(), request.contentCode(), request.title().strip(),
                request.description().strip(), request.contentType(), request.rating(), request.durationMinutes(),
                request.playbackUrl(), request.active());
        auditService.record(adminId, "CONTENT_CREATED", "STREAMING_CONTENT", id);
        return id;
    }

    @Transactional
    public void updateContent(long adminId, long contentId, ContentRequest request) {
        if (!List.of("MOVIE", "SERIES", "CLIP").contains(request.contentType())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Unsupported streaming content type");
        }
        int updated = jdbcTemplate.update(
                "UPDATE streaming_content SET category_id = ?, content_code = ?, title = ?, description = ?, "
                        + "content_type = ?, rating = ?, duration_minutes = ?, playback_url = ?, active = ?, "
                        + "updated_at = CURRENT_TIMESTAMP WHERE id = ?",
                request.categoryId(), request.contentCode(), request.title().strip(), request.description().strip(),
                request.contentType(), request.rating(), request.durationMinutes(), request.playbackUrl(),
                request.active(), contentId);
        if (updated != 1) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Streaming content was not found");
        }
        auditService.record(adminId, "CONTENT_UPDATED", "STREAMING_CONTENT", contentId);
    }

    private String like(String value) {
        return value == null || value.isBlank() ? null : "%" + value.strip() + "%";
    }
}
