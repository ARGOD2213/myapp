package com.connectone.payment.service;

/**
 * Port to an external payment gateway. {@link SimulatedPaymentProvider} is the only implementation;
 * a real gateway adapter would implement this interface and be selected by configuration.
 */
public interface PaymentProvider {

    /** Resolves a gateway callback into a provider-confirmed outcome and reference. */
    ProviderResult confirm(String outcome, String callbackId);

    record ProviderResult(String outcome, String reference) {
    }
}
