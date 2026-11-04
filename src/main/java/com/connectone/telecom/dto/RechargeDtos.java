package com.connectone.telecom.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public final class RechargeDtos {

    private RechargeDtos() {
    }

    public record AddMobileNumberRequest(
            @NotBlank @Pattern(regexp = "^\\+?[1-9][0-9]{7,14}$") String phoneNumber) {
    }

    public record InitiateRechargeRequest(
            @Positive long mobileNumberId,
            @Positive long planId,
            @NotBlank @Size(min = 8, max = 100) String idempotencyKey,
            @Size(max = 40) String couponCode) {
    }

    public record MobileNumberResponse(
            long id,
            String phoneNumber,
            String status,
            LocalDateTime createdAt) {
    }

    public record RechargeResponse(
            long id,
            long paymentId,
            long mobileNumberId,
            String planCode,
            BigDecimal amount,
            String status,
            LocalDateTime expiresAt,
            LocalDateTime createdAt) {
    }
}
