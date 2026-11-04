package com.connectone.support.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.ResponseStatus;

import com.connectone.common.config.ConnectOnePrincipal;
import com.connectone.common.exception.ApiException;
import com.connectone.support.dto.SupportDtos.AssignmentRequest;
import com.connectone.support.dto.SupportDtos.CommentRequest;
import com.connectone.support.dto.SupportDtos.CreateTicketRequest;
import com.connectone.support.dto.SupportDtos.TransitionRequest;
import com.connectone.support.service.SupportService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/support")
public class SupportController {

    private final SupportService service;

    public SupportController(SupportService service) {
        this.service = service;
    }

    @PostMapping("/tickets")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> create(
            @AuthenticationPrincipal ConnectOnePrincipal principal, @Valid @RequestBody CreateTicketRequest request) {
        requireCustomer(principal);
        return service.create(principal.userId(), request);
    }

    @GetMapping("/tickets/me")
    public List<Map<String, Object>> myTickets(@AuthenticationPrincipal ConnectOnePrincipal principal) {
        requireCustomer(principal);
        return service.customerTickets(principal.userId());
    }

    @GetMapping("/tickets")
    public List<Map<String, Object>> search(
            @AuthenticationPrincipal ConnectOnePrincipal principal,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String priority,
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sort) {
        requireAgent(principal);
        return service.search(status, priority, category, page, size, sort);
    }

    @GetMapping("/tickets/{ticketId}")
    public Map<String, Object> details(
            @AuthenticationPrincipal ConnectOnePrincipal principal, @PathVariable long ticketId) {
        boolean agent = isAgent(principal);
        requireCustomerOrAgent(principal);
        return service.details(principal.userId(), ticketId, agent);
    }

    @GetMapping("/tickets/{ticketId}/history")
    public List<Map<String, Object>> history(
            @AuthenticationPrincipal ConnectOnePrincipal principal, @PathVariable long ticketId) {
        boolean agent = isAgent(principal);
        requireCustomerOrAgent(principal);
        return service.history(principal.userId(), ticketId, agent);
    }

    @PostMapping("/tickets/{ticketId}/comments")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void comment(
            @AuthenticationPrincipal ConnectOnePrincipal principal,
            @PathVariable long ticketId,
            @Valid @RequestBody CommentRequest request) {
        boolean agent = isAgent(principal);
        requireCustomerOrAgent(principal);
        service.comment(principal.userId(), ticketId, request, agent);
    }

    @PutMapping("/tickets/{ticketId}/assignment")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void assign(
            @AuthenticationPrincipal ConnectOnePrincipal principal,
            @PathVariable long ticketId,
            @Valid @RequestBody AssignmentRequest request) {
        requireAgent(principal);
        service.assign(principal.userId(), ticketId, request);
    }

    @PutMapping("/tickets/{ticketId}/status")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void transition(
            @AuthenticationPrincipal ConnectOnePrincipal principal,
            @PathVariable long ticketId,
            @Valid @RequestBody TransitionRequest request) {
        requireAgent(principal);
        service.transition(principal.userId(), ticketId, request.status());
    }

    private boolean isAgent(ConnectOnePrincipal principal) {
        return principal.roles().contains("ADMIN") || principal.roles().contains("SUPPORT_AGENT");
    }

    private void requireCustomer(ConnectOnePrincipal principal) {
        if (!principal.roles().contains("CUSTOMER")) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Customer role is required");
        }
    }

    private void requireAgent(ConnectOnePrincipal principal) {
        if (!isAgent(principal) || !principal.permissions().contains("SUPPORT_MANAGE")) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Support agent role is required");
        }
    }

    private void requireCustomerOrAgent(ConnectOnePrincipal principal) {
        if (!principal.roles().contains("CUSTOMER") && !isAgent(principal)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Customer or support-agent role is required");
        }
    }
}
