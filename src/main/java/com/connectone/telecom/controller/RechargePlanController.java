package com.connectone.telecom.controller;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.validation.annotation.Validated;

import com.connectone.telecom.service.RechargePlanService;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/api/v1/recharge-plans")
@Validated
public class RechargePlanController {

    private final RechargePlanService rechargePlanService;

    public RechargePlanController(RechargePlanService rechargePlanService) {
        this.rechargePlanService = rechargePlanService;
    }

    @GetMapping
    public List<RechargePlanResponse> getAvailablePlans(
            @RequestParam(required = false) @Size(max = 100) String q,
            @RequestParam(required = false) @Positive BigDecimal maxPrice,
            @RequestParam(required = false) @Positive Integer maxValidityDays,
            @RequestParam(required = false) @PositiveOrZero Long minDataAllowanceMb) {
        return rechargePlanService.searchAvailable(q, maxPrice, maxValidityDays, minDataAllowanceMb).stream()
                .map(plan -> new RechargePlanResponse(
                        plan.getId(),
                        plan.getPlanCode(),
                        plan.getName(),
                        plan.getPrice(),
                        plan.getValidityDays(),
                        plan.getDataAllowanceMb(),
                        plan.getVoiceMinutes()))
                .toList();
    }

    public record RechargePlanResponse(
            Long id,
            String code,
            String name,
            BigDecimal price,
            Integer validityDays,
            Long dataAllowanceMb,
            Integer voiceMinutes) {
    }
}
