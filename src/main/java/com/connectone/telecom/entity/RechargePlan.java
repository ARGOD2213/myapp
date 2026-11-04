package com.connectone.telecom.entity;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "recharge_plan")
public class RechargePlan implements java.io.Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "plan_code", nullable = false, unique = true, length = 32)
    private String planCode;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @Column(name = "validity_days", nullable = false)
    private Integer validityDays;

    @Column(name = "data_allowance_mb", nullable = false)
    private Long dataAllowanceMb;

    @Column(name = "voice_minutes", nullable = false)
    private Integer voiceMinutes;

    @Column(nullable = false)
    private Boolean active;

    protected RechargePlan() {
    }

    public Long getId() {
        return id;
    }

    public String getPlanCode() {
        return planCode;
    }

    public String getName() {
        return name;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public Integer getValidityDays() {
        return validityDays;
    }

    public Long getDataAllowanceMb() {
        return dataAllowanceMb;
    }

    public Integer getVoiceMinutes() {
        return voiceMinutes;
    }

    public Boolean getActive() {
        return active;
    }
}
