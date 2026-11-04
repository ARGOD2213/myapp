package com.connectone.identity.service;

/** Port for OTP generation/delivery. A real SMS/email OTP service would replace the simulated one. */
public interface OtpProvider {

    String generateCode();
}
