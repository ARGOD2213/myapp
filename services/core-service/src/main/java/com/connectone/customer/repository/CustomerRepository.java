package com.connectone.customer.repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.connectone.common.exception.ApiException;
import com.connectone.customer.dto.CustomerDtos.ActivityResponse;
import com.connectone.customer.dto.CustomerDtos.CustomerSummary;
import com.connectone.customer.dto.CustomerDtos.ProfileResponse;

import org.springframework.http.HttpStatus;

@Repository
public class CustomerRepository {

    private static final Map<String, String> SORT_COLUMNS = Map.of(
            "createdAt", "c.created_at",
            "email", "u.email",
            "firstName", "c.first_name",
            "lastName", "c.last_name",
            "accountStatus", "c.account_status");

    private final JdbcTemplate jdbcTemplate;

    public CustomerRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<ProfileResponse> findProfileByUserId(long userId) {
        List<ProfileResponse> profiles = jdbcTemplate.query(
                "SELECT c.id, u.email, c.first_name, c.last_name, c.phone_number, "
                        + "c.account_status, c.created_at "
                        + "FROM customer_profile c JOIN app_user u ON u.id = c.user_id WHERE c.user_id = ?",
                (result, row) -> new ProfileResponse(
                        result.getLong("id"),
                        result.getString("email"),
                        result.getString("first_name"),
                        result.getString("last_name"),
                        result.getString("phone_number"),
                        result.getString("account_status"),
                        toLocalDateTime(result.getTimestamp("created_at"))),
                userId);
        return profiles.stream().findFirst();
    }

    public Page<CustomerSummary> search(String search, String accountStatus, Pageable requestedPage) {
        int pageNumber = Math.max(requestedPage.getPageNumber(), 0);
        int pageSize = Math.min(Math.max(requestedPage.getPageSize(), 1), 100);
        Sort.Order order = requestedPage.getSort().stream().findFirst()
                .orElse(Sort.Order.desc("createdAt"));
        String sortColumn = SORT_COLUMNS.get(order.getProperty());
        if (sortColumn == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Unsupported customer sort field");
        }

        StringBuilder where = new StringBuilder(" WHERE 1 = 1");
        List<Object> parameters = new ArrayList<>();
        if (search != null && !search.isBlank()) {
            where.append(" AND (LOWER(u.email) LIKE ? OR LOWER(c.first_name) LIKE ? "
                    + "OR LOWER(c.last_name) LIKE ? OR c.phone_number LIKE ?)");
            String term = "%" + search.strip().toLowerCase(java.util.Locale.ROOT) + "%";
            parameters.add(term);
            parameters.add(term);
            parameters.add(term);
            parameters.add(term);
        }
        if (accountStatus != null && !accountStatus.isBlank()) {
            where.append(" AND c.account_status = ?");
            parameters.add(accountStatus.strip().toUpperCase(java.util.Locale.ROOT));
        }

        String from = " FROM customer_profile c JOIN app_user u ON u.id = c.user_id" + where;
        Long total = jdbcTemplate.queryForObject("SELECT COUNT(*)" + from, Long.class, parameters.toArray());
        List<Object> pageParameters = new ArrayList<>(parameters);
        pageParameters.add(pageSize);
        pageParameters.add((long) pageNumber * pageSize);
        String direction = order.isAscending() ? "ASC" : "DESC";
        List<CustomerSummary> results = jdbcTemplate.query(
                "SELECT c.id, u.email, c.first_name, c.last_name, c.phone_number, "
                        + "c.account_status, c.created_at" + from
                        + " ORDER BY " + sortColumn + " " + direction + ", c.id DESC LIMIT ? OFFSET ?",
                (result, row) -> new CustomerSummary(
                        result.getLong("id"),
                        result.getString("email"),
                        result.getString("first_name"),
                        result.getString("last_name"),
                        result.getString("phone_number"),
                        result.getString("account_status"),
                        toLocalDateTime(result.getTimestamp("created_at"))),
                pageParameters.toArray());
        return new PageImpl<>(results, PageRequest.of(pageNumber, pageSize, Sort.by(order)), total == null ? 0 : total);
    }

    public List<ActivityResponse> findActivity(long userId, int limit) {
        return jdbcTemplate.query(
                "SELECT a.id, a.activity_type, a.summary, a.created_at "
                        + "FROM customer_activity a JOIN customer_profile c ON c.id = a.customer_id "
                        + "WHERE c.user_id = ? ORDER BY a.created_at DESC, a.id DESC LIMIT ?",
                (result, row) -> new ActivityResponse(
                        result.getLong("id"),
                        result.getString("activity_type"),
                        result.getString("summary"),
                        toLocalDateTime(result.getTimestamp("created_at"))),
                userId,
                Math.min(Math.max(limit, 1), 100));
    }

    private static LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }
}
