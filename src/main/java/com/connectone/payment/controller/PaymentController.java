package com.connectone.payment.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
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
import com.connectone.payment.dto.PaymentDtos.ConfirmPaymentRequest;
import com.connectone.payment.dto.PaymentDtos.PaymentResponse;
import com.connectone.payment.dto.PaymentDtos.RefundRequest;
import com.connectone.payment.dto.PaymentDtos.RefundResponse;
import com.connectone.payment.service.PaymentService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @GetMapping("/me")
    public List<PaymentResponse> myPayments(@AuthenticationPrincipal ConnectOnePrincipal principal) {
        return paymentService.history(principal.userId());
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPPORT_AGENT') and hasAuthority('PAYMENT_MONITOR')")
    public List<PaymentResponse> monitor(@RequestParam(required = false) String status) {
        return paymentService.monitor(status);
    }

    @PostMapping("/{paymentId}/confirm")
    public PaymentResponse confirm(
            @AuthenticationPrincipal ConnectOnePrincipal principal,
            @PathVariable long paymentId,
            @Valid @RequestBody ConfirmPaymentRequest request) {
        return paymentService.confirm(principal.userId(), paymentId, request);
    }

    @PostMapping("/{paymentId}/refunds")
    @ResponseStatus(HttpStatus.CREATED)
    public RefundResponse refund(
            @AuthenticationPrincipal ConnectOnePrincipal principal,
            @PathVariable long paymentId,
            @Valid @RequestBody RefundRequest request) {
        return paymentService.refund(principal.userId(), paymentId, request);
    }
}
