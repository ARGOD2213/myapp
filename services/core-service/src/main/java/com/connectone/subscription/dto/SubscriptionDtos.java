package com.connectone.subscription.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public final class SubscriptionDtos {

    private SubscriptionDtos() {
    }

    public record PlanResponse(
            long id,
            String code,
            String name,
            BigDecimal price,
            int billingPeriodDays) {
    }

    public record PurchaseRequest(
            @Positive long planId,
            boolean autoRenew,
            @NotBlank @Size(min = 8, max = 100) String idempotencyKey,
            @Size(max = 40) String couponCode) {
    }

    public record SubscriptionResponse(
            long id,
            long paymentId,
            String planCode,
            String planName,
            String status,
            LocalDateTime startsAt,
            LocalDateTime expiresAt,
            boolean autoRenew) {
    }

    public record EntitlementResponse(
            long subscriptionId,
            String planCode,
            boolean entitled,
            LocalDateTime expiresAt) {
    }
}
