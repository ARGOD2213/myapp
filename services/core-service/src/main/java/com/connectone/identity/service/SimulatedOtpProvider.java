package com.connectone.identity.service;

import java.security.SecureRandom;

import org.springframework.stereotype.Component;

@Component
public class SimulatedOtpProvider implements OtpProvider {

    private final SecureRandom secureRandom = new SecureRandom();

    @Override
    public String generateCode() {
        return "%06d".formatted(secureRandom.nextInt(1_000_000));
    }
}
