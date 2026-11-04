package com.connectone.payment.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotNull;

public final class PaymentDtos {

    private PaymentDtos() {
    }

    public record PaymentResponse(
            long id,
            BigDecimal amount,
            String currency,
            String status,
            String providerReference,
            LocalDateTime createdAt) {
    }

    public record ConfirmPaymentRequest(
            @NotBlank @Size(min = 8, max = 100) String callbackId,
            @NotBlank @Pattern(regexp = "^(SUCCEEDED|FAILED)$") String outcome) {
    }

    public record RefundRequest(
            @NotNull @DecimalMin("0.01") @Digits(integer = 10, fraction = 2) BigDecimal amount,
            @NotBlank @Size(max = 250) String reason,
            @NotBlank @Size(min = 8, max = 100) String idempotencyKey) {
    }

    public record RefundResponse(
            long id,
            long paymentId,
            BigDecimal amount,
            String status,
            String reason,
            LocalDateTime createdAt) {
    }
}
