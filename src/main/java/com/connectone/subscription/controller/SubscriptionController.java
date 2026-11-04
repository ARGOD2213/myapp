package com.connectone.subscription.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.connectone.common.config.ConnectOnePrincipal;
import com.connectone.subscription.dto.SubscriptionDtos.EntitlementResponse;
import com.connectone.subscription.dto.SubscriptionDtos.PlanResponse;
import com.connectone.subscription.dto.SubscriptionDtos.PurchaseRequest;
import com.connectone.subscription.dto.SubscriptionDtos.SubscriptionResponse;
import com.connectone.subscription.service.SubscriptionService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/api/v1/subscriptions")
public class SubscriptionController {

    private final SubscriptionService subscriptionService;

    public SubscriptionController(SubscriptionService subscriptionService) {
        this.subscriptionService = subscriptionService;
    }

    @GetMapping("/plans")
    public List<PlanResponse> plans() {
        return subscriptionService.plans();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SubscriptionResponse purchase(
            @AuthenticationPrincipal ConnectOnePrincipal principal,
            @Valid @RequestBody PurchaseRequest request) {
        return subscriptionService.purchase(principal.userId(), request);
    }

    @PostMapping("/{subscriptionId}/renew")
    public SubscriptionResponse renew(
            @AuthenticationPrincipal ConnectOnePrincipal principal,
            @PathVariable long subscriptionId,
            @Valid @RequestBody RenewalRequest request) {
        return subscriptionService.renew(principal.userId(), subscriptionId, request.idempotencyKey());
    }

    @PostMapping("/{subscriptionId}/cancel")
    public SubscriptionResponse cancel(
            @AuthenticationPrincipal ConnectOnePrincipal principal,
            @PathVariable long subscriptionId) {
        return subscriptionService.cancel(principal.userId(), subscriptionId);
    }

    @GetMapping("/{subscriptionId}/entitlement")
    public EntitlementResponse entitlement(
            @AuthenticationPrincipal ConnectOnePrincipal principal,
            @PathVariable long subscriptionId) {
        return subscriptionService.entitlement(principal.userId(), subscriptionId);
    }

    @GetMapping("/me")
    public List<SubscriptionResponse> history(@AuthenticationPrincipal ConnectOnePrincipal principal) {
        return subscriptionService.history(principal.userId());
    }

    public record RenewalRequest(@NotBlank @Size(min = 8, max = 100) String idempotencyKey) {
    }
}
