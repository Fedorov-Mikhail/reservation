package com.mikey.reservation.shared.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("booking.policy")
public record BookingPolicyProperties(
        @DefaultValue("1") @Min(1) @Max(1440) int minDurationMinutes,
        @DefaultValue("24") @Min(1) @Max(8760) int maxDurationHours,
        @DefaultValue("365") @Min(1) @Max(3650) int horizonDays,
        @DefaultValue("31") @Min(1) @Max(366) int maxSearchDays) {
    public BookingPolicyProperties {
        if (minDurationMinutes > (long) maxDurationHours * 60)
            throw new IllegalArgumentException("Minimum booking duration exceeds maximum");
    }
}

