package com.connectone.admin.service;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.connectone.admin.dto.AdminDtos.CouponRequest;
import com.connectone.admin.dto.AdminDtos.OfferRequest;
import com.connectone.admin.dto.AdminDtos.RechargePlanRequest;
import com.connectone.admin.dto.AdminDtos.RedeemCouponRequest;
import com.connectone.admin.dto.AdminDtos.RolePermissionRequest;
import com.connectone.admin.dto.AdminDtos.SubscriptionPlanRequest;
import com.connectone.admin.dto.AdminDtos.SupportAgentRequest;
import com.connectone.admin.dto.AdminDtos.UserRoleRequest;
import com.connectone.common.audit.AuditService;
import com.connectone.common.database.GeneratedKeySupport;
import com.connectone.common.database.JdbcRowSupport;
import com.connectone.common.exception.ApiException;

@Service
public class AdminService {

    private final com.connectone.common.cache.PlanCatalogueCache catalogueCache;
    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    public AdminService(JdbcTemplate jdbcTemplate, PasswordEncoder passwordEncoder, AuditService auditService,
            com.connectone.common.cache.PlanCatalogueCache catalogueCache) {
        this.catalogueCache = catalogueCache;
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> rechargePlans() {
        return JdbcRowSupport.camelCaseRows(jdbcTemplate.queryForList(
                "SELECT id, plan_code, name, price, validity_days, data_allowance_mb, voice_minutes, active "
                        + "FROM recharge_plan ORDER BY id"));
    }

    @Transactional
    public long createRechargePlan(long actor, RechargePlanRequest request) {
        long id = GeneratedKeySupport.insert(jdbcTemplate,
                "INSERT INTO recharge_plan (plan_code, name, price, validity_days, data_allowance_mb, "
                        + "voice_minutes, active) VALUES (?, ?, ?, ?, ?, ?, ?)",
                request.planCode(), request.name().strip(), request.price(), request.validityDays(),
                request.dataAllowanceMb(), request.voiceMinutes(), request.active());
        auditService.record(actor, "RECHARGE_PLAN_CREATED", "RECHARGE_PLAN", id);
        catalogueCache.evictRechargePlansAfterCommit();
        return id;
    }

    @Transactional
    public void updateRechargePlan(long actor, long planId, RechargePlanRequest request) {
        int rows = jdbcTemplate.update(
                "UPDATE recharge_plan SET plan_code = ?, name = ?, price = ?, validity_days = ?, "
                        + "data_allowance_mb = ?, voice_minutes = ?, active = ?, updated_at = CURRENT_TIMESTAMP "
                        + "WHERE id = ?",
                request.planCode(), request.name().strip(), request.price(), request.validityDays(),
                request.dataAllowanceMb(), request.voiceMinutes(), request.active(), planId);
        requireUpdated(rows, "Recharge plan");
        auditService.record(actor, "RECHARGE_PLAN_UPDATED", "RECHARGE_PLAN", planId);
        catalogueCache.evictRechargePlansAfterCommit();
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> subscriptionPlans() {
        return JdbcRowSupport.camelCaseRows(jdbcTemplate.queryForList(
                "SELECT id, plan_code, name, price, billing_period_days, active FROM subscription_plan ORDER BY id"));
    }

    @Transactional
    public long createSubscriptionPlan(long actor, SubscriptionPlanRequest request) {
        long id = GeneratedKeySupport.insert(jdbcTemplate,
                "INSERT INTO subscription_plan (plan_code, name, price, billing_period_days, active) "
                        + "VALUES (?, ?, ?, ?, ?)",
                request.planCode(), request.name().strip(), request.price(),
                request.billingPeriodDays(), request.active());
        auditService.record(actor, "SUBSCRIPTION_PLAN_CREATED", "SUBSCRIPTION_PLAN", id);
        return id;
    }

    @Transactional
    public void updateSubscriptionPlan(long actor, long planId, SubscriptionPlanRequest request) {
        int rows = jdbcTemplate.update(
                "UPDATE subscription_plan SET plan_code = ?, name = ?, price = ?, billing_period_days = ?, "
                        + "active = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?",
                request.planCode(), request.name().strip(), request.price(), request.billingPeriodDays(),
                request.active(), planId);
        requireUpdated(rows, "Subscription plan");
        auditService.record(actor, "SUBSCRIPTION_PLAN_UPDATED", "SUBSCRIPTION_PLAN", planId);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> offers() {
        return JdbcRowSupport.camelCaseRows(jdbcTemplate.queryForList(
                "SELECT o.id, o.offer_code, o.name, o.discount_percent, o.starts_at, o.ends_at, o.active, "
                        + "c.id AS coupon_id, c.coupon_code, c.usage_count, c.usage_limit, c.active AS coupon_active "
                        + "FROM offer o LEFT JOIN coupon c ON c.offer_id = o.id ORDER BY o.id, c.id"));
    }

    @Transactional
    public long createOffer(long actor, OfferRequest request) {
        if (!request.endsAt().isAfter(request.startsAt())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Offer end time must follow its start time");
        }
        long id = GeneratedKeySupport.insert(jdbcTemplate,
                "INSERT INTO offer (offer_code, name, discount_percent, starts_at, ends_at, active) "
                        + "VALUES (?, ?, ?, ?, ?, ?)",
                request.offerCode(), request.name().strip(), request.discountPercent(),
                Timestamp.valueOf(request.startsAt()), Timestamp.valueOf(request.endsAt()), request.active());
        auditService.record(actor, "OFFER_CREATED", "OFFER", id);
        return id;
    }

    @Transactional
    public long createCoupon(long actor, long offerId, CouponRequest request) {
        long id = GeneratedKeySupport.insert(jdbcTemplate,
                "INSERT INTO coupon (coupon_code, offer_id, usage_limit) "
                        + "VALUES (?, ?, ?)",
                request.couponCode(), offerId, request.usageLimit());
        auditService.record(actor, "COUPON_CREATED", "COUPON", id);
        return id;
    }

    @Transactional
    public void offerActive(long actor, long offerId, boolean active) {
        int rows = jdbcTemplate.update("UPDATE offer SET active = ? WHERE id = ?", active, offerId);
        requireUpdated(rows, "Offer");
        auditService.record(actor, "OFFER_STATUS_CHANGED", "OFFER", offerId);
    }

    @Transactional
    public void couponActive(long actor, long couponId, boolean active) {
        int rows = jdbcTemplate.update("UPDATE coupon SET active = ? WHERE id = ?", active, couponId);
        requireUpdated(rows, "Coupon");
        auditService.record(actor, "COUPON_STATUS_CHANGED", "COUPON", couponId);
    }

    @Transactional
    public Map<String, Object> redeem(long userId, RedeemCouponRequest request) {
        long customerId = customerId(userId);
        Map<String, Object> coupon;
        try {
            coupon = jdbcTemplate.queryForMap(
                    "SELECT c.id, c.usage_count, c.usage_limit, c.active, o.discount_percent, "
                            + "o.starts_at, o.ends_at, o.active AS offer_active FROM coupon c "
                            + "JOIN offer o ON o.id = c.offer_id WHERE c.coupon_code = ? FOR UPDATE",
                    request.code());
        } catch (org.springframework.dao.EmptyResultDataAccessException exception) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Coupon was not found");
        }
        Timestamp starts = (Timestamp) coupon.get("starts_at");
        Timestamp ends = (Timestamp) coupon.get("ends_at");
        if (!Boolean.TRUE.equals(coupon.get("active")) || !Boolean.TRUE.equals(coupon.get("offer_active"))
                || !starts.toLocalDateTime().isBefore(java.time.LocalDateTime.now())
                || !ends.toLocalDateTime().isAfter(java.time.LocalDateTime.now())) {
            throw new ApiException(HttpStatus.CONFLICT, "Coupon is not active");
        }
        if (((Number) coupon.get("usage_count")).intValue()
                >= ((Number) coupon.get("usage_limit")).intValue()) {
            throw new ApiException(HttpStatus.CONFLICT, "Coupon usage limit has been reached");
        }
        try {
            jdbcTemplate.update(
                    "INSERT INTO coupon_redemption (coupon_id, customer_id) VALUES (?, ?)",
                    coupon.get("id"), customerId);
        } catch (org.springframework.dao.DuplicateKeyException exception) {
            throw new ApiException(HttpStatus.CONFLICT, "Coupon has already been redeemed by this customer");
        }
        jdbcTemplate.update("UPDATE coupon SET usage_count = usage_count + 1 WHERE id = ?", coupon.get("id"));
        return Map.of("couponCode", request.code(), "discountPercent", (BigDecimal) coupon.get("discount_percent"));
    }

    @Transactional
    public void customerStatus(long actor, long customerId, String status) {
        if (!List.of("ACTIVE", "SUSPENDED", "CLOSED").contains(status)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Unsupported customer status");
        }
        Long userId = jdbcTemplate.query(
                "SELECT user_id FROM customer_profile WHERE id = ?",
                result -> result.next() ? result.getLong(1) : null, customerId);
        if (userId == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Customer was not found");
        }
        jdbcTemplate.update(
                "UPDATE customer_profile SET account_status = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?",
                status, customerId);
        jdbcTemplate.update(
                "UPDATE app_user SET account_status = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?",
                "ACTIVE".equals(status) ? "ACTIVE" : "DISABLED", userId);
        auditService.record(actor, "CUSTOMER_STATUS_CHANGED", "CUSTOMER", customerId);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> supportAgents() {
        return JdbcRowSupport.camelCaseRows(jdbcTemplate.queryForList(
                "SELECT u.id, u.email, u.account_status, u.created_at FROM app_user u "
                        + "JOIN user_role ur ON ur.user_id = u.id JOIN app_role r ON r.id = ur.role_id "
                        + "WHERE r.role_code = 'SUPPORT_AGENT' ORDER BY u.email"));
    }

    @Transactional
    public long createSupportAgent(long actor, SupportAgentRequest request) {
        String email = request.email().strip().toLowerCase(java.util.Locale.ROOT);
        long id = GeneratedKeySupport.insert(jdbcTemplate,
                "INSERT INTO app_user (email, password_hash, account_status) VALUES (?, ?, 'ACTIVE')",
                email, passwordEncoder.encode(request.password()));
        jdbcTemplate.update(
                "INSERT INTO user_role (user_id, role_id) SELECT ?, id FROM app_role WHERE role_code = 'SUPPORT_AGENT'",
                id);
        auditService.record(actor, "SUPPORT_AGENT_CREATED", "APP_USER", id);
        return id;
    }

    @Transactional
    public void supportAgentStatus(long actor, long userId, String status) {
        if (!List.of("ACTIVE", "DISABLED").contains(status)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Support-agent status must be ACTIVE or DISABLED");
        }
        int rows = jdbcTemplate.update(
                "UPDATE app_user SET account_status = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ? "
                        + "AND EXISTS (SELECT 1 FROM user_role ur JOIN app_role r ON r.id = ur.role_id "
                        + "WHERE ur.user_id = app_user.id AND r.role_code = 'SUPPORT_AGENT')",
                status, userId);
        requireUpdated(rows, "Support agent");
        auditService.record(actor, "SUPPORT_AGENT_STATUS_CHANGED", "APP_USER", userId);
    }

    @Transactional
    public void userRole(long actor, long userId, UserRoleRequest request) {
        jdbcTemplate.update("INSERT INTO user_role (user_id, role_id) VALUES (?, ?)", userId, request.roleId());
        auditService.record(actor, "USER_ROLE_ASSIGNED", "APP_USER", userId);
    }

    @Transactional
    public void removeUserRole(long actor, long userId, long roleId) {
        int rows = jdbcTemplate.update("DELETE FROM user_role WHERE user_id = ? AND role_id = ?", userId, roleId);
        requireUpdated(rows, "User role assignment");
        auditService.record(actor, "USER_ROLE_REMOVED", "APP_USER", userId);
    }

    @Transactional
    public void rolePermission(long actor, long roleId, RolePermissionRequest request) {
        jdbcTemplate.update(
                "INSERT INTO role_permission (role_id, permission_id) VALUES (?, ?)", roleId, request.permissionId());
        auditService.record(actor, "ROLE_PERMISSION_ASSIGNED", "APP_ROLE", roleId);
    }

    @Transactional
    public void removeRolePermission(long actor, long roleId, long permissionId) {
        int rows = jdbcTemplate.update(
                "DELETE FROM role_permission WHERE role_id = ? AND permission_id = ?", roleId, permissionId);
        requireUpdated(rows, "Role permission assignment");
        auditService.record(actor, "ROLE_PERMISSION_REMOVED", "APP_ROLE", roleId);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> rolesAndPermissions() {
        return JdbcRowSupport.camelCaseRows(jdbcTemplate.queryForList(
                "SELECT r.id AS role_id, r.role_code, p.id AS permission_id, p.permission_code "
                        + "FROM app_role r LEFT JOIN role_permission rp ON rp.role_id = r.id "
                        + "LEFT JOIN app_permission p ON p.id = rp.permission_id ORDER BY r.id, p.id"));
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> audit(long page, long size) {
        long boundedSize = Math.min(Math.max(size, 1), 200);
        return JdbcRowSupport.camelCaseRows(jdbcTemplate.queryForList(
                "SELECT id, actor_user_id, event_type, entity_type, entity_id, correlation_id, created_at "
                        + "FROM audit_event ORDER BY created_at DESC, id DESC LIMIT ? OFFSET ?",
                boundedSize, Math.max(page, 0) * boundedSize));
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> bookings() {
        return JdbcRowSupport.camelCaseRows(jdbcTemplate.queryForList(
                "SELECT b.id, b.booking_code, b.customer_id, i.item_type, i.title, b.seat_or_slot, "
                        + "b.status, b.amount, b.created_at FROM booking_record b "
                        + "JOIN booking_catalog_item i ON i.id = b.catalog_item_id "
                        + "ORDER BY b.created_at DESC LIMIT 200"));
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

    private void requireUpdated(int rows, String resource) {
        if (rows != 1) {
            throw new ApiException(HttpStatus.NOT_FOUND, resource + " was not found");
        }
    }
}
