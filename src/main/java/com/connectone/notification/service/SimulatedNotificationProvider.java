package com.connectone.notification.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class SimulatedNotificationProvider implements NotificationProvider {

    private final boolean simulatedFailure;

    public SimulatedNotificationProvider(
            @Value("${connectone.notification.simulated-failure:false}") boolean simulatedFailure) {
        this.simulatedFailure = simulatedFailure;
    }

    @Override
    public boolean deliver() {
        return !simulatedFailure;
    }
}
