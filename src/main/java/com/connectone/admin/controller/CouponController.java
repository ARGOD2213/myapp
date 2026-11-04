package com.connectone.admin.controller;

import java.util.Map;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.connectone.admin.dto.AdminDtos.RedeemCouponRequest;
import com.connectone.admin.service.AdminService;
import com.connectone.common.config.ConnectOnePrincipal;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/coupons")
public class CouponController {

    private final AdminService service;

    public CouponController(AdminService service) {
        this.service = service;
    }

    @PostMapping("/redeem")
    public Map<String, Object> redeem(
            @AuthenticationPrincipal ConnectOnePrincipal principal,
            @Valid @RequestBody RedeemCouponRequest request) {
        return service.redeem(principal.userId(), request);
    }
}
