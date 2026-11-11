package com.connectone.telecom.service;

import java.util.List;
import java.math.BigDecimal;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.connectone.telecom.entity.RechargePlan;
import com.connectone.telecom.repository.RechargePlanRepository;

@Service
@Transactional(readOnly = true)
public class RechargePlanService {

    private final RechargePlanRepository rechargePlanRepository;

    public RechargePlanService(RechargePlanRepository rechargePlanRepository) {
        this.rechargePlanRepository = rechargePlanRepository;
    }

    @org.springframework.cache.annotation.Cacheable(
            cacheNames = com.connectone.common.cache.CacheConfiguration.RECHARGE_PLANS,
            key = "T(String).valueOf(#query) + '|' + #maxPrice + '|' + #maxValidityDays + '|' + #minDataAllowanceMb")
    public List<RechargePlan> searchAvailable(
            String query,
            BigDecimal maxPrice,
            Integer maxValidityDays,
            Long minDataAllowanceMb) {
        return rechargePlanRepository.searchAvailable(
                query == null || query.isBlank() ? null : query.strip(),
                maxPrice,
                maxValidityDays,
                minDataAllowanceMb);
    }
}
