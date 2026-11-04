package com.connectone.customer.dto;

import java.time.LocalDateTime;

public final class CustomerDtos {

    private CustomerDtos() {
    }

    public record ProfileResponse(
            long customerId,
            String email,
            String firstName,
            String lastName,
            String phoneNumber,
            String accountStatus,
            LocalDateTime createdAt) {
    }

    public record CustomerSummary(
            long customerId,
            String email,
            String firstName,
            String lastName,
            String phoneNumber,
            String accountStatus,
            LocalDateTime createdAt) {
    }

    public record ActivityResponse(
            long id,
            String activityType,
            String summary,
            LocalDateTime createdAt) {
    }
}
