package com.connectone.customer.controller;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.connectone.common.config.ConnectOnePrincipal;
import com.connectone.customer.dto.CustomerDtos.ActivityResponse;
import com.connectone.customer.dto.CustomerDtos.CustomerSummary;
import com.connectone.customer.dto.CustomerDtos.ProfileResponse;
import com.connectone.customer.service.CustomerService;

@RestController
@RequestMapping("/api/v1/customers")
public class CustomerController {

    private final CustomerService customerService;

    public CustomerController(CustomerService customerService) {
        this.customerService = customerService;
    }

    @GetMapping("/me")
    public ProfileResponse getMyProfile(@AuthenticationPrincipal ConnectOnePrincipal principal) {
        return customerService.getMyProfile(principal.userId());
    }

    @GetMapping("/me/activity")
    public List<ActivityResponse> getMyActivity(
            @AuthenticationPrincipal ConnectOnePrincipal principal,
            @RequestParam(defaultValue = "20") int limit) {
        return customerService.getMyActivity(principal.userId(), limit);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPPORT_AGENT') and hasAuthority('CUSTOMER_READ')")
    public Page<CustomerSummary> searchCustomers(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String status,
            @PageableDefault(size = 20) Pageable pageable) {
        return customerService.search(q, status, pageable);
    }
}
