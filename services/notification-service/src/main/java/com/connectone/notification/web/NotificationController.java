package com.connectone.notification.web;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.connectone.notification.security.JwtVerifier.Principal;
import com.connectone.notification.service.NotificationStore;

/** Same URLs and JSON shapes the monolith exposed, so the frontend only had to be re-pointed at the gateway. */
@RestController
@RequestMapping("/api/v1")
public class NotificationController {

    private static final String MONITOR = "hasAnyRole('ADMIN', 'SUPPORT_AGENT') and hasAuthority('PAYMENT_MONITOR')";

    private final NotificationStore store;

    public NotificationController(NotificationStore store) {
        this.store = store;
    }

    @GetMapping("/notifications/me")
    @PreAuthorize("hasRole('CUSTOMER')")
    public List<Map<String, Object>> mine(@AuthenticationPrincipal Principal principal) {
        return store.history(principal.userId());
    }

    @PostMapping("/notifications/{id}/read")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<Void> markRead(@AuthenticationPrincipal Principal principal, @PathVariable long id) {
        store.markRead(principal.userId(), id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/admin/notification-deliveries")
    @PreAuthorize(MONITOR)
    public Map<String, Object> deliveries(@RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return store.deliveries(status, page, size);
    }

    @GetMapping("/admin/notifications/summary")
    @PreAuthorize(MONITOR)
    public List<Map<String, Object>> summary() {
        return store.summaryByStatus();
    }

    @PostMapping("/admin/notifications/retry")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, Integer> retry() {
        return Map.of("processed", store.retryFailed());
    }
}
