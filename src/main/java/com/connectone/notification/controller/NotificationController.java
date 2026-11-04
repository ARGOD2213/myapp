package com.connectone.notification.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.connectone.common.config.ConnectOnePrincipal;
import com.connectone.notification.service.NotificationService;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationService service;

    public NotificationController(NotificationService service) {
        this.service = service;
    }

    @GetMapping("/me")
    public List<Map<String, Object>> history(@AuthenticationPrincipal ConnectOnePrincipal principal) {
        return service.history(principal.userId());
    }

    @PostMapping("/{notificationId}/read")
    public ResponseEntity<Void> markRead(
            @AuthenticationPrincipal ConnectOnePrincipal principal,
            @PathVariable long notificationId) {
        service.markRead(principal.userId(), notificationId);
        return ResponseEntity.noContent().build();
    }
}
