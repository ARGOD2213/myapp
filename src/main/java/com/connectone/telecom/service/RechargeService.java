package com.connectone.telecom.service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.connectone.admin.service.CheckoutCouponService;
import com.connectone.common.exception.ApiException;
import com.connectone.telecom.dto.RechargeDtos.MobileNumberResponse;
import com.connectone.telecom.dto.RechargeDtos.RechargeResponse;

@Service
public class RechargeService {

    private final JdbcTemplate jdbcTemplate;
    private final CheckoutCouponService coupons;

    public RechargeService(JdbcTemplate jdbcTemplate, CheckoutCouponService coupons) {
        this.jdbcTemplate = jdbcTemplate;
        this.coupons = coupons;
    }

    @Transactional(readOnly = true)
    public List<MobileNumberResponse> listMobileNumbers(long userId) {
        long customerId = activeCustomerId(userId);
        return jdbcTemplate.query(
                "SELECT id, phone_number, status, created_at FROM mobile_number "
                        + "WHERE customer_id = ? ORDER BY created_at DESC, id DESC",
                (result, row) -> new MobileNumberResponse(
                        result.getLong("id"),
                        result.getString("phone_number"),
                        result.getString("status"),
                        result.getTimestamp("created_at").toLocalDateTime()),
                customerId);
    }

    @Transactional
    public MobileNumberResponse addMobileNumber(long userId, String phoneNumber) {
        long customerId = activeCustomerId(userId);
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO mobile_number (customer_id, phone_number) VALUES (?, ?)",
                    new String[] {"id"});
            statement.setLong(1, customerId);
            statement.setString(2, phoneNumber);
            return statement;
        }, keyHolder);
        Number id = keyHolder.getKey();
        if (id == null) {
            throw new IllegalStateException("Database did not return the mobile number identifier");
        }
        jdbcTemplate.update(
                "INSERT INTO customer_activity (customer_id, activity_type, summary) VALUES (?, ?, ?)",
                customerId, "MOBILE_NUMBER_ADDED", "A mobile number was added to the account");
        return jdbcTemplate.queryForObject(
                "SELECT id, phone_number, status, created_at FROM mobile_number WHERE id = ?",
                (result, row) -> new MobileNumberResponse(
                        result.getLong("id"),
                        result.getString("phone_number"),
                        result.getString("status"),
                        result.getTimestamp("created_at").toLocalDateTime()),
                id.longValue());
    }

    @Transactional
    public RechargeResponse initiate(long userId, long mobileNumberId, long planId, String idempotencyKey, String couponCode) {
        long customerId = activeCustomerId(userId);
        Map<String, Object> mobile = queryOneOrNotFound(
                "SELECT id, status FROM mobile_number WHERE id = ? AND customer_id = ? FOR UPDATE",
                mobileNumberId, customerId);
        if (!"ACTIVE".equals(mobile.get("status"))) {
            throw new ApiException(HttpStatus.CONFLICT, "The mobile number is not active");
        }
        Map<String, Object> plan = queryOneOrNotFound(
                "SELECT id, plan_code, price, validity_days FROM recharge_plan WHERE id = ? AND active = TRUE",
                planId);

        Map<String, Object> existing = findRechargeByIdempotencyKey(idempotencyKey);
        if (existing != null) {
            if (((Number) existing.get("customer_id")).longValue() != customerId
                    || ((Number) existing.get("mobile_number_id")).longValue() != mobileNumberId
                    || ((Number) existing.get("plan_id")).longValue() != planId) {
                throw new ApiException(HttpStatus.CONFLICT, "Idempotency key was already used for a different request");
            }
            return response(existing);
        }

        long rechargeId = insertRecharge(
                customerId,
                mobileNumberId,
                planId,
                (BigDecimal) plan.get("price"),
                idempotencyKey);
        String paymentKey = "recharge-" + sha256(idempotencyKey);
        jdbcTemplate.update(
                "INSERT INTO payment_record "
                        + "(customer_id, recharge_id, amount, currency, status, idempotency_key) "
                        + "VALUES (?, ?, ?, 'INR', 'INITIATED', ?)",
                customerId, rechargeId, plan.get("price"), paymentKey);
        coupons.applyToPayment(customerId, couponCode, paymentKey, (BigDecimal) plan.get("price"));
        jdbcTemplate.update(
                "INSERT INTO customer_activity (customer_id, activity_type, summary) VALUES (?, ?, ?)",
                customerId, "RECHARGE_INITIATED", "Recharge request " + rechargeId + " was initiated");
        Map<String, Object> created = queryOneOrNotFound(
                "SELECT r.id, r.customer_id, r.mobile_number_id, r.plan_id, p.plan_code, r.amount, r.status, "
                        + "r.expires_at, r.created_at, pay.id AS payment_id "
                        + "FROM recharge_transaction r JOIN recharge_plan p ON p.id = r.plan_id "
                        + "JOIN payment_record pay ON pay.recharge_id = r.id WHERE r.id = ?",
                rechargeId);
        return response(created);
    }

    @Transactional(readOnly = true)
    public Page<RechargeResponse> history(long userId, Pageable requestedPage) {
        long customerId = activeCustomerId(userId);
        int page = Math.max(requestedPage.getPageNumber(), 0);
        int size = Math.min(Math.max(requestedPage.getPageSize(), 1), 100);
        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM recharge_transaction WHERE customer_id = ?", Long.class, customerId);
        List<RechargeResponse> rows = jdbcTemplate.query(
                "SELECT r.id, r.customer_id, r.mobile_number_id, r.plan_id, p.plan_code, r.amount, r.status, "
                        + "r.expires_at, r.created_at, pay.id AS payment_id "
                        + "FROM recharge_transaction r JOIN recharge_plan p ON p.id = r.plan_id "
                        + "JOIN payment_record pay ON pay.recharge_id = r.id "
                        + "WHERE r.customer_id = ? ORDER BY r.created_at DESC, r.id DESC LIMIT ? OFFSET ?",
                (result, row) -> new RechargeResponse(
                        result.getLong("id"),
                        result.getLong("payment_id"),
                        result.getLong("mobile_number_id"),
                        result.getString("plan_code"),
                        result.getBigDecimal("amount"),
                        result.getString("status"),
                        toLocalDateTime(result.getTimestamp("expires_at")),
                        toLocalDateTime(result.getTimestamp("created_at"))),
                customerId, size, (long) page * size);
        return new PageImpl<>(rows, PageRequest.of(page, size), total == null ? 0 : total);
    }

    private long insertRecharge(
            long customerId,
            long mobileNumberId,
            long planId,
            BigDecimal amount,
            String idempotencyKey) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO recharge_transaction "
                            + "(customer_id, mobile_number_id, plan_id, amount, status, idempotency_key) "
                            + "VALUES (?, ?, ?, ?, 'PENDING', ?)",
                    new String[] {"id"});
            statement.setLong(1, customerId);
            statement.setLong(2, mobileNumberId);
            statement.setLong(3, planId);
            statement.setBigDecimal(4, amount);
            statement.setString(5, idempotencyKey);
            return statement;
        }, keyHolder);
        Number id = keyHolder.getKey();
        if (id == null) {
            throw new IllegalStateException("Database did not return the recharge identifier");
        }
        return id.longValue();
    }

    private Map<String, Object> findRechargeByIdempotencyKey(String key) {
        try {
            return jdbcTemplate.queryForMap(
                    "SELECT r.id, r.customer_id, r.mobile_number_id, r.plan_id, p.plan_code, r.amount, r.status, "
                            + "r.expires_at, r.created_at, pay.id AS payment_id "
                            + "FROM recharge_transaction r JOIN recharge_plan p ON p.id = r.plan_id "
                            + "JOIN payment_record pay ON pay.recharge_id = r.id WHERE r.idempotency_key = ?",
                    key);
        } catch (EmptyResultDataAccessException exception) {
            return null;
        }
    }

    private long activeCustomerId(long userId) {
        try {
            Map<String, Object> customer = jdbcTemplate.queryForMap(
                    "SELECT id, account_status FROM customer_profile WHERE user_id = ?", userId);
            if (!"ACTIVE".equals(customer.get("account_status"))) {
                throw new ApiException(HttpStatus.FORBIDDEN, "Customer account is not active");
            }
            return ((Number) customer.get("id")).longValue();
        } catch (EmptyResultDataAccessException exception) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Customer profile was not found");
        }
    }

    private Map<String, Object> queryOneOrNotFound(String sql, Object... args) {
        try {
            return jdbcTemplate.queryForMap(sql, args);
        } catch (EmptyResultDataAccessException exception) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Requested resource was not found");
        }
    }

    private RechargeResponse response(Map<String, Object> row) {
        return new RechargeResponse(
                ((Number) row.get("id")).longValue(),
                ((Number) row.get("payment_id")).longValue(),
                ((Number) row.get("mobile_number_id")).longValue(),
                (String) row.get("plan_code"),
                (BigDecimal) row.get("amount"),
                (String) row.get("status"),
                toLocalDateTime((Timestamp) row.get("expires_at")),
                toLocalDateTime((Timestamp) row.get("created_at")));
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to hash an idempotency key", exception);
        }
    }

    private LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }
}
