package com.connectone.admin.controller;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
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

import com.connectone.admin.service.AdminOperationsService;
import com.connectone.common.config.ConnectOnePrincipal;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** Back-office endpoints. ADMIN only (payment/refund/notification monitoring is also open to SUPPORT_AGENT). */
@RestController
@RequestMapping("/api/v1/admin")
public class AdminOperationsController {

    private static final String ADMIN = "hasRole('ADMIN') and hasAuthority('CUSTOMER_MANAGE') and hasAuthority('PLAN_MANAGE')";
    private static final String MONITOR = "hasAnyRole('ADMIN', 'SUPPORT_AGENT') and hasAuthority('PAYMENT_MONITOR')";

    private final AdminOperationsService service;

    public AdminOperationsController(AdminOperationsService service) {
        this.service = service;
    }

    @GetMapping("/dashboard")
    @PreAuthorize(ADMIN)
    public Map<String, Object> dashboard() {
        return service.dashboard();
    }

    @GetMapping("/customers/{customerId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPPORT_AGENT') and hasAuthority('CUSTOMER_READ')")
    public Map<String, Object> customer(@PathVariable long customerId) {
        return service.customerDetail(customerId);
    }

    @GetMapping("/payments")
    @PreAuthorize(MONITOR)
    public Page<Map<String, Object>> payments(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String reconciliation,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Long customerId,
            @PageableDefault(size = 20) Pageable pageable) {
        return service.payments(status, reconciliation, q, customerId, pageable);
    }

    @PostMapping("/payments/{paymentId}/reconcile")
    @PreAuthorize(ADMIN)
    public Map<String, Object> reconcile(
            @AuthenticationPrincipal ConnectOnePrincipal principal,
            @PathVariable long paymentId,
            @Valid @RequestBody ReconcileRequest request) {
        return service.reconcile(principal.userId(), paymentId, request.outcome(), request.note());
    }

    @GetMapping("/refunds")
    @PreAuthorize(MONITOR)
    public Page<Map<String, Object>> refunds(
            @RequestParam(required = false) String status, @PageableDefault(size = 20) Pageable pageable) {
        return service.refunds(status, pageable);
    }

    @GetMapping("/notification-deliveries")
    @PreAuthorize(MONITOR)
    public Page<Map<String, Object>> notificationDeliveries(
            @RequestParam(required = false) String status, @PageableDefault(size = 20) Pageable pageable) {
        return service.notificationDeliveries(status, pageable);
    }

    @GetMapping("/permissions")
    @PreAuthorize(ADMIN)
    public List<Map<String, Object>> permissions() {
        return service.permissions();
    }

    @GetMapping("/streaming-content")
    @PreAuthorize(ADMIN)
    public List<Map<String, Object>> streamingContent() {
        return service.streamingContent();
    }

    @PutMapping("/streaming-content/{id}")
    @PreAuthorize(ADMIN)
    public void updateStreamingContent(
            @AuthenticationPrincipal ConnectOnePrincipal principal, @PathVariable long id,
            @Valid @RequestBody StreamingContentUpdate request) {
        service.updateStreamingContent(principal.userId(), id, request.title(), request.description(), request.active());
    }

    @GetMapping("/booking-items")
    @PreAuthorize(ADMIN)
    public List<Map<String, Object>> bookingItems() {
        return service.bookingItems();
    }

    @PostMapping("/booking-items")
    @PreAuthorize(ADMIN)
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Long> createBookingItem(
            @AuthenticationPrincipal ConnectOnePrincipal principal, @Valid @RequestBody BookingItemCreate request) {
        return Map.of("id", service.createBookingItem(principal.userId(), request.itemType(), request.itemCode(),
                request.title(), request.location(), request.startsAt(), request.capacity(), request.price()));
    }

    @PutMapping("/booking-items/{id}")
    @PreAuthorize(ADMIN)
    public void updateBookingItem(
            @AuthenticationPrincipal ConnectOnePrincipal principal, @PathVariable long id,
            @Valid @RequestBody BookingItemUpdate request) {
        service.updateBookingItem(principal.userId(), id, request.title(), request.price(), request.available());
    }

    public record ReconcileRequest(
            @NotBlank @Pattern(regexp = "^(SUCCEEDED|FAILED)$") String outcome,
            @Size(max = 200) String note) {
    }

    public record StreamingContentUpdate(
            @NotBlank @Size(max = 160) String title, @NotBlank @Size(max = 2000) String description, boolean active) {
    }

    public record BookingItemCreate(
            @NotBlank @Pattern(regexp = "^(BUS|MOVIE|EVENT)$") String itemType,
            @NotBlank @Size(max = 40) String itemCode,
            @NotBlank @Size(max = 160) String title,
            @Size(max = 160) String location,
            @NotNull @Future LocalDateTime startsAt,
            @Positive int capacity,
            @NotNull @DecimalMin("0.00") BigDecimal price) {
    }

    public record BookingItemUpdate(
            @NotBlank @Size(max = 160) String title, @NotNull @DecimalMin("0.00") BigDecimal price, boolean available) {
    }
}
