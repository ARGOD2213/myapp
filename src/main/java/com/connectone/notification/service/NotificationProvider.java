package com.connectone.notification.service;

/** Port for notification delivery (SMS/email/push). The simulated provider can be told to fail. */
public interface NotificationProvider {

    /** @return true when the provider accepted the message */
    boolean deliver();
}
