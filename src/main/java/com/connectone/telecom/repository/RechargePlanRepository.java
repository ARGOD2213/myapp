package com.connectone.telecom.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.connectone.telecom.entity.RechargePlan;

public interface RechargePlanRepository extends JpaRepository<RechargePlan, Long> {

    @Query("""
            select p from RechargePlan p
            where p.active = true
              and (cast(:query as string) is null or lower(p.name) like lower(concat('%', cast(:query as string), '%'))
                   or lower(p.planCode) like lower(concat('%', cast(:query as string), '%')))
              and (:maxPrice is null or p.price <= :maxPrice)
              and (:maxValidityDays is null or p.validityDays <= :maxValidityDays)
              and (:minDataAllowanceMb is null or p.dataAllowanceMb >= :minDataAllowanceMb)
            order by p.price asc, p.id asc
            """)
    List<RechargePlan> searchAvailable(
            @Param("query") String query,
            @Param("maxPrice") java.math.BigDecimal maxPrice,
            @Param("maxValidityDays") Integer maxValidityDays,
            @Param("minDataAllowanceMb") Long minDataAllowanceMb);
}
