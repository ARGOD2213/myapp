package com.connectone.identity.dto;

import java.util.List;

public final class AuthDtos {

    private AuthDtos() {
    }

    public record RegistrationResponse(String challengeId, String message, String developmentOtp) {
    }

    public record TokenResponse(
            String accessToken,
            String refreshToken,
            long expiresInSeconds,
            List<String> roles) {
    }
}
