package com.connectone.subscription.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class SubscriptionExpiryJob {

    private static final Logger LOGGER = LoggerFactory.getLogger(SubscriptionExpiryJob.class);
    private final SubscriptionService subscriptionService;

    public SubscriptionExpiryJob(SubscriptionService subscriptionService) {
        this.subscriptionService = subscriptionService;
    }

    @Scheduled(fixedDelayString = "${connectone.subscription.expiry-scan-ms:60000}")
    public void expireSubscriptions() {
        int reminders = subscriptionService.remindExpiringSubscriptions(200);
        int expired = subscriptionService.expireDueSubscriptions(200);
        if (expired > 0) {
            LOGGER.info("Expired {} subscription records", expired);
        }
        if (reminders > 0) {
            LOGGER.info("Queued {} subscription expiry reminders", reminders);
        }
    }
}
