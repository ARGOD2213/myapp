package com.connectone.streaming.controller;

import java.util.List;
import java.util.Map;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.connectone.common.config.ConnectOnePrincipal;
import com.connectone.streaming.dto.StreamingDtos.ContentRequest;
import com.connectone.streaming.service.StreamingService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/streaming")
public class StreamingController {

    private final StreamingService service;

    public StreamingController(StreamingService service) {
        this.service = service;
    }

    @GetMapping("/content")
    public List<Map<String, Object>> catalogue(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String type) {
        return service.catalogue(category, q, type);
    }

    @GetMapping("/content/{contentId}")
    public Map<String, Object> details(@PathVariable long contentId) {
        return service.details(contentId);
    }

    @PostMapping("/content/{contentId}/play")
    public Map<String, Object> play(
            @AuthenticationPrincipal ConnectOnePrincipal principal,
            @PathVariable long contentId,
            @RequestParam(defaultValue = "0") int progressSeconds) {
        return service.play(principal.userId(), contentId, progressSeconds);
    }

    @GetMapping("/history/me")
    public List<Map<String, Object>> history(@AuthenticationPrincipal ConnectOnePrincipal principal) {
        return service.watchHistory(principal.userId());
    }

    @PostMapping("/admin/content")
    public Map<String, Long> create(
            @AuthenticationPrincipal ConnectOnePrincipal principal,
            @Valid @RequestBody ContentRequest request) {
        requireAdmin(principal);
        return Map.of("id", service.createContent(principal.userId(), request));
    }

    @org.springframework.web.bind.annotation.PutMapping("/admin/content/{contentId}")
    public void update(
            @AuthenticationPrincipal ConnectOnePrincipal principal,
            @PathVariable long contentId,
            @Valid @RequestBody ContentRequest request) {
        requireAdmin(principal);
        service.updateContent(principal.userId(), contentId, request);
    }

    private void requireAdmin(ConnectOnePrincipal principal) {
        if (!principal.roles().contains("ADMIN") || !principal.permissions().contains("PLAN_MANAGE")) {
            throw new com.connectone.common.exception.ApiException(
                    org.springframework.http.HttpStatus.FORBIDDEN, "Administrator role is required");
        }
    }
}
