package com.connectone.booking.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;

public final class BookingDtos {

    private BookingDtos() {
    }

    public record CreateRequest(
            long catalogItemId,
            @NotBlank @Size(max = 5) @Pattern(regexp = "[1-9][0-9]{0,4}") String seatOrSlot,
            @NotBlank @Size(min = 8, max = 100) String idempotencyKey,
            @Size(max = 40) String couponCode) {
    }

    public record BookingResponse(
            long id,
            String bookingCode,
            long catalogItemId,
            String itemType,
            String title,
            String seatOrSlot,
            String status,
            BigDecimal amount,
            Long paymentId,
            LocalDateTime startsAt,
            LocalDateTime createdAt) {
    }
}
