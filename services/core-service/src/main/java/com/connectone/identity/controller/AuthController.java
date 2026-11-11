package com.connectone.identity.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.connectone.identity.dto.AuthDtos.RegistrationResponse;
import com.connectone.identity.dto.AuthDtos.TokenResponse;
import com.connectone.identity.service.AuthService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public RegistrationResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(
                request.email(),
                request.password(),
                request.firstName(),
                request.lastName(),
                request.phoneNumber());
    }

    @PostMapping("/verify-otp")
    public TokenResponse verifyOtp(@Valid @RequestBody OtpVerificationRequest request) {
        return authService.verifyOtp(request.challengeId(), request.code());
    }

    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request.email(), request.password());
    }

    @PostMapping("/refresh")
    public TokenResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return authService.refresh(request.refreshToken());
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@Valid @RequestBody RefreshRequest request) {
        authService.logout(request.refreshToken());
    }

    public record RegisterRequest(
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(min = 12, max = 72)
                    @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*[0-9]).+$",
                            message = "must contain lowercase, uppercase, and numeric characters")
                    String password,
            @NotBlank @Size(max = 80) String firstName,
            @NotBlank @Size(max = 80) String lastName,
            @NotBlank @Pattern(regexp = "^\\+?[1-9][0-9]{7,14}$") String phoneNumber) {
    }

    public record LoginRequest(
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(max = 72) String password) {
    }

    public record OtpVerificationRequest(
            @NotBlank @Size(max = 36) String challengeId,
            @NotBlank @Pattern(regexp = "^[0-9]{6}$") String code) {
    }

    public record RefreshRequest(@NotBlank @Size(max = 100) String refreshToken) {
    }
}
