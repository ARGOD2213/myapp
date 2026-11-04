package com.connectone.customer.controller;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.connectone.admin.service.CheckoutCouponService;
import com.connectone.common.config.ConnectOnePrincipal;
import com.connectone.common.database.JdbcRowSupport;
import com.connectone.common.exception.ApiException;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Customer self-service: profile edits, primary/disconnected mobile numbers, own refunds, coupon preview. */
@RestController
public class SelfServiceController {

    private final JdbcTemplate jdbcTemplate;
    private final CheckoutCouponService coupons;

    public SelfServiceController(JdbcTemplate jdbcTemplate, CheckoutCouponService coupons) {
        this.jdbcTemplate = jdbcTemplate;
        this.coupons = coupons;
    }

    @PutMapping("/api/v1/customers/me")
    @Transactional
    public ResponseEntity<Void> updateProfile(
            @AuthenticationPrincipal ConnectOnePrincipal principal, @Valid @RequestBody ProfileUpdate request) {
        int updated = jdbcTemplate.update(
                "UPDATE customer_profile SET first_name = ?, last_name = ?, display_language = ?, updated_at = CURRENT_TIMESTAMP "
                        + "WHERE user_id = ? AND account_status = 'ACTIVE'",
                request.firstName().strip(), request.lastName().strip(), request.displayLanguage(), principal.userId());
        if (updated != 1) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Customer account is not active");
        }
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/api/v1/mobile-numbers/{id}/primary")
    @Transactional
    public ResponseEntity<Void> makePrimary(@AuthenticationPrincipal ConnectOnePrincipal principal, @PathVariable long id) {
        long customerId = customerId(principal.userId());
        jdbcTemplate.queryForList("SELECT id FROM mobile_number WHERE customer_id = ? FOR UPDATE", customerId);
        Integer eligible = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM mobile_number WHERE id = ? AND customer_id = ? AND status = 'ACTIVE'", Integer.class, id, customerId);
        if (eligible == null || eligible == 0) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Active mobile number was not found");
        }
        jdbcTemplate.update("UPDATE mobile_number SET is_primary = FALSE WHERE customer_id = ? AND is_primary", customerId);
        jdbcTemplate.update("UPDATE mobile_number SET is_primary = TRUE WHERE id = ?", id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/api/v1/mobile-numbers/{id}")
    @Transactional
    public ResponseEntity<Void> disconnect(@AuthenticationPrincipal ConnectOnePrincipal principal, @PathVariable long id) {
        long customerId = customerId(principal.userId());
        Integer pending = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM recharge_transaction WHERE mobile_number_id = ? AND status IN ('PENDING', 'PROCESSING')",
                Integer.class, id);
        if (pending != null && pending > 0) {
            throw new ApiException(HttpStatus.CONFLICT, "The number has a recharge in progress");
        }
        int updated = jdbcTemplate.update(
                "UPDATE mobile_number SET status = 'DISCONNECTED', is_primary = FALSE WHERE id = ? AND customer_id = ? AND status <> 'DISCONNECTED'",
                id, customerId);
        if (updated != 1) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Mobile number was not found");
        }
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/v1/refunds/me")
    @Transactional(readOnly = true)
    public List<Map<String, Object>> myRefunds(@AuthenticationPrincipal ConnectOnePrincipal principal) {
        return JdbcRowSupport.camelCaseRows(jdbcTemplate.queryForList(
                "SELECT r.id, r.payment_id, r.amount, p.amount AS payment_amount, r.status, r.reason, r.created_at "
                        + "FROM refund_record r JOIN payment_record p ON p.id = r.payment_id "
                        + "WHERE p.customer_id = ? ORDER BY r.created_at DESC, r.id DESC LIMIT 200", customerId(principal.userId())));
    }

    @PostMapping("/api/v1/coupons/validate")
    public CheckoutCouponService.Quote validateCoupon(
            @AuthenticationPrincipal ConnectOnePrincipal principal, @Valid @RequestBody CouponQuoteRequest request) {
        return coupons.quote(customerId(principal.userId()), request.code().strip(), request.amount());
    }

    private long customerId(long userId) {
        List<Long> ids = jdbcTemplate.queryForList("SELECT id FROM customer_profile WHERE user_id = ?", Long.class, userId);
        if (ids.isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Customer profile was not found");
        }
        return ids.get(0);
    }

    public record ProfileUpdate(
            @NotBlank @Size(max = 80) String firstName,
            @NotBlank @Size(max = 80) String lastName,
            @NotBlank @Pattern(regexp = "^[a-z]{2}(-[A-Z]{2})?$") String displayLanguage) {
    }

    public record CouponQuoteRequest(@NotBlank @Size(max = 40) String code, @NotNull @DecimalMin("0.01") BigDecimal amount) {
    }
}
