package com.connectone.telecom.controller;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.connectone.common.config.ConnectOnePrincipal;
import com.connectone.telecom.dto.RechargeDtos.AddMobileNumberRequest;
import com.connectone.telecom.dto.RechargeDtos.InitiateRechargeRequest;
import com.connectone.telecom.dto.RechargeDtos.MobileNumberResponse;
import com.connectone.telecom.dto.RechargeDtos.RechargeResponse;
import com.connectone.telecom.service.RechargeService;

import jakarta.validation.Valid;

@RestController
public class RechargeController {

    private final RechargeService rechargeService;

    public RechargeController(RechargeService rechargeService) {
        this.rechargeService = rechargeService;
    }

    @GetMapping("/api/v1/mobile-numbers")
    public List<MobileNumberResponse> listMobileNumbers(
            @AuthenticationPrincipal ConnectOnePrincipal principal) {
        return rechargeService.listMobileNumbers(principal.userId());
    }

    @PostMapping("/api/v1/mobile-numbers")
    @ResponseStatus(HttpStatus.CREATED)
    public MobileNumberResponse addMobileNumber(
            @AuthenticationPrincipal ConnectOnePrincipal principal,
            @Valid @RequestBody AddMobileNumberRequest request) {
        return rechargeService.addMobileNumber(principal.userId(), request.phoneNumber());
    }

    @PostMapping("/api/v1/recharges")
    @ResponseStatus(HttpStatus.CREATED)
    public RechargeResponse initiate(
            @AuthenticationPrincipal ConnectOnePrincipal principal,
            @Valid @RequestBody InitiateRechargeRequest request) {
        return rechargeService.initiate(
                principal.userId(),
                request.mobileNumberId(),
                request.planId(),
                request.idempotencyKey(),
                request.couponCode());
    }

    @GetMapping("/api/v1/recharges/me")
    public Page<RechargeResponse> history(
            @AuthenticationPrincipal ConnectOnePrincipal principal,
            @PageableDefault(size = 20) Pageable pageable) {
        return rechargeService.history(principal.userId(), pageable);
    }
}
