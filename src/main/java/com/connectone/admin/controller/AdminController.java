package com.connectone.admin.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.connectone.admin.dto.AdminDtos.CouponRequest;
import com.connectone.admin.dto.AdminDtos.ActiveRequest;
import com.connectone.admin.dto.AdminDtos.OfferRequest;
import com.connectone.admin.dto.AdminDtos.RechargePlanRequest;
import com.connectone.admin.dto.AdminDtos.RolePermissionRequest;
import com.connectone.admin.dto.AdminDtos.SubscriptionPlanRequest;
import com.connectone.admin.dto.AdminDtos.SupportAgentRequest;
import com.connectone.admin.dto.AdminDtos.UserRoleRequest;
import com.connectone.admin.service.AdminService;
import com.connectone.common.config.ConnectOnePrincipal;
import com.connectone.notification.service.NotificationService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('ADMIN') and hasAuthority('CUSTOMER_MANAGE') "
        + "and hasAuthority('PLAN_MANAGE') and hasAuthority('SUPPORT_MANAGE') "
        + "and hasAuthority('PAYMENT_MONITOR')")
public class AdminController {

    private final AdminService service;
    private final NotificationService notifications;

    public AdminController(AdminService service, NotificationService notifications) {
        this.service = service;
        this.notifications = notifications;
    }

    @GetMapping("/recharge-plans")
    public List<Map<String, Object>> rechargePlans() {
        return service.rechargePlans();
    }

    @PostMapping("/recharge-plans")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Long> createRechargePlan(
            @AuthenticationPrincipal ConnectOnePrincipal principal, @Valid @RequestBody RechargePlanRequest request) {
        return Map.of("id", service.createRechargePlan(principal.userId(), request));
    }

    @PutMapping("/recharge-plans/{planId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updateRechargePlan(
            @AuthenticationPrincipal ConnectOnePrincipal principal,
            @PathVariable long planId,
            @Valid @RequestBody RechargePlanRequest request) {
        service.updateRechargePlan(principal.userId(), planId, request);
    }

    @GetMapping("/subscription-plans")
    public List<Map<String, Object>> subscriptionPlans() {
        return service.subscriptionPlans();
    }

    @PostMapping("/subscription-plans")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Long> createSubscriptionPlan(
            @AuthenticationPrincipal ConnectOnePrincipal principal,
            @Valid @RequestBody SubscriptionPlanRequest request) {
        return Map.of("id", service.createSubscriptionPlan(principal.userId(), request));
    }

    @PutMapping("/subscription-plans/{planId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updateSubscriptionPlan(
            @AuthenticationPrincipal ConnectOnePrincipal principal,
            @PathVariable long planId,
            @Valid @RequestBody SubscriptionPlanRequest request) {
        service.updateSubscriptionPlan(principal.userId(), planId, request);
    }

    @GetMapping("/offers")
    public List<Map<String, Object>> offers() {
        return service.offers();
    }

    @PostMapping("/offers")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Long> createOffer(
            @AuthenticationPrincipal ConnectOnePrincipal principal, @Valid @RequestBody OfferRequest request) {
        return Map.of("id", service.createOffer(principal.userId(), request));
    }

    @PostMapping("/offers/{offerId}/coupons")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Long> createCoupon(
            @AuthenticationPrincipal ConnectOnePrincipal principal,
            @PathVariable long offerId,
            @Valid @RequestBody CouponRequest request) {
        return Map.of("id", service.createCoupon(principal.userId(), offerId, request));
    }

    @PutMapping("/offers/{offerId}/active")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void setOfferActive(
            @AuthenticationPrincipal ConnectOnePrincipal principal,
            @PathVariable long offerId,
            @Valid @RequestBody ActiveRequest request) {
        service.offerActive(principal.userId(), offerId, request.active());
    }

    @PutMapping("/coupons/{couponId}/active")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void setCouponActive(
            @AuthenticationPrincipal ConnectOnePrincipal principal,
            @PathVariable long couponId,
            @Valid @RequestBody ActiveRequest request) {
        service.couponActive(principal.userId(), couponId, request.active());
    }

    @GetMapping("/support-agents")
    public List<Map<String, Object>> supportAgents() {
        return service.supportAgents();
    }

    @PostMapping("/support-agents")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Long> createSupportAgent(
            @AuthenticationPrincipal ConnectOnePrincipal principal,
            @Valid @RequestBody SupportAgentRequest request) {
        requirePermission(principal, "SUPPORT_MANAGE");
        return Map.of("id", service.createSupportAgent(principal.userId(), request));
    }

    @PutMapping("/support-agents/{userId}/status")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updateSupportAgent(
            @AuthenticationPrincipal ConnectOnePrincipal principal,
            @PathVariable long userId,
            @RequestBody com.connectone.admin.dto.AdminDtos.CustomerStatusRequest request) {
        requirePermission(principal, "SUPPORT_MANAGE");
        service.supportAgentStatus(principal.userId(), userId, request.status());
    }

    @PostMapping("/users/{userId}/roles")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void assignUserRole(
            @AuthenticationPrincipal ConnectOnePrincipal principal,
            @PathVariable long userId,
            @Valid @RequestBody UserRoleRequest request) {
        requirePermission(principal, "SUPPORT_MANAGE");
        service.userRole(principal.userId(), userId, request);
    }

    @org.springframework.web.bind.annotation.DeleteMapping("/users/{userId}/roles/{roleId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeUserRole(
            @AuthenticationPrincipal ConnectOnePrincipal principal,
            @PathVariable long userId,
            @PathVariable long roleId) {
        service.removeUserRole(principal.userId(), userId, roleId);
    }

    @GetMapping("/roles")
    public List<Map<String, Object>> roles() {
        return service.rolesAndPermissions();
    }

    @PostMapping("/roles/{roleId}/permissions")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void assignPermission(
            @AuthenticationPrincipal ConnectOnePrincipal principal,
            @PathVariable long roleId,
            @Valid @RequestBody RolePermissionRequest request) {
        requirePermission(principal, "SUPPORT_MANAGE");
        service.rolePermission(principal.userId(), roleId, request);
    }

    @org.springframework.web.bind.annotation.DeleteMapping("/roles/{roleId}/permissions/{permissionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removePermission(
            @AuthenticationPrincipal ConnectOnePrincipal principal,
            @PathVariable long roleId,
            @PathVariable long permissionId) {
        service.removeRolePermission(principal.userId(), roleId, permissionId);
    }

    @PutMapping("/customers/{customerId}/status")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updateCustomerStatus(
            @AuthenticationPrincipal ConnectOnePrincipal principal,
            @PathVariable long customerId,
            @Valid @RequestBody com.connectone.admin.dto.AdminDtos.CustomerStatusRequest request) {
        requirePermission(principal, "CUSTOMER_MANAGE");
        service.customerStatus(principal.userId(), customerId, request.status());
    }

    @GetMapping("/bookings")
    public List<Map<String, Object>> bookings() {
        return service.bookings();
    }

    @GetMapping("/audit")
    public List<Map<String, Object>> audit(
            @RequestParam(defaultValue = "0") long page, @RequestParam(defaultValue = "50") long size) {
        return service.audit(page, size);
    }

    @PostMapping("/notifications/retry")
    public Map<String, Integer> retryNotifications() {
        return Map.of("processed", notifications.retryFailed());
    }

    private void requirePermission(ConnectOnePrincipal principal, String permission) {
        if (!principal.permissions().contains(permission)) {
            throw new com.connectone.common.exception.ApiException(
                    HttpStatus.FORBIDDEN, "Required permission is missing: " + permission);
        }
    }
}
