package com.connectone.payment.service;

import org.springframework.stereotype.Component;

/** Simulated gateway: trusts the caller-supplied outcome and derives a stable reference from the callback id. */
@Component
public class SimulatedPaymentProvider implements PaymentProvider {

    @Override
    public ProviderResult confirm(String outcome, String callbackId) {
        return new ProviderResult(
                outcome,
                "SIM-" + java.util.UUID.nameUUIDFromBytes(
                        callbackId.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }
}
