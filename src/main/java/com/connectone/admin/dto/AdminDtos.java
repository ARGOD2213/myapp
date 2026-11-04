package com.connectone.admin.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class AdminDtos {

    private AdminDtos() {
    }

    public record CustomerStatusRequest(@NotBlank String status) {
    }

    public record ActiveRequest(boolean active) {
    }

    public record RechargePlanRequest(
            @NotBlank @Size(max = 32) String planCode,
            @NotBlank @Size(max = 100) String name,
            @NotNull @DecimalMin(value = "0.01") BigDecimal price,
            @Min(1) int validityDays,
            @Min(0) long dataAllowanceMb,
            @Min(0) int voiceMinutes,
            boolean active) {
    }

    public record SubscriptionPlanRequest(
            @NotBlank @Size(max = 32) String planCode,
            @NotBlank @Size(max = 100) String name,
            @NotNull @DecimalMin(value = "0.01") BigDecimal price,
            @Min(1) int billingPeriodDays,
            boolean active) {
    }

    public record OfferRequest(
            @NotBlank @Size(max = 40) String offerCode,
            @NotBlank @Size(max = 120) String name,
            @NotNull @DecimalMin(value = "0.01") @DecimalMax("100.00") BigDecimal discountPercent,
            @NotNull LocalDateTime startsAt,
            @NotNull LocalDateTime endsAt,
            boolean active) {
    }

    public record CouponRequest(@NotBlank @Size(max = 40) String couponCode, @Min(1) int usageLimit) {
    }

    public record SupportAgentRequest(
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(min = 12, max = 72) String password) {
    }

    public record UserRoleRequest(@Min(1) long roleId) {
    }

    public record RolePermissionRequest(@Min(1) long permissionId) {
    }

    public record RedeemCouponRequest(@NotBlank @Size(max = 40) String code) {
    }
}
