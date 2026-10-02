package com.mikey.reservation.booking.domain;

import com.mikey.reservation.shared.api.ApiException;
import com.mikey.reservation.shared.config.BookingPolicyProperties;
import org.springframework.stereotype.Component;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Component
public class BookingPolicy {
    private final BookingPolicyProperties properties;
    public BookingPolicy(BookingPolicyProperties properties) { this.properties = properties; }

    public void validateCreation(TimeInterval interval, Instant now) {
        if (interval.start().isBefore(now)) throw ApiException.invalid("Booking cannot start in the past.");
        if (interval.duration().compareTo(Duration.ofMinutes(properties.minDurationMinutes())) < 0
                || interval.duration().compareTo(Duration.ofHours(properties.maxDurationHours())) > 0)
            throw ApiException.invalid("Booking duration is outside the configured limits.");
        if (interval.end().isAfter(now.plus(properties.horizonDays(), ChronoUnit.DAYS)))
            throw ApiException.invalid("Booking exceeds the allowed horizon.");
    }
    public void validateSearch(TimeInterval window, int minMinutes) {
        if (window.duration().compareTo(Duration.ofDays(properties.maxSearchDays())) > 0)
            throw ApiException.invalid("Search window exceeds the configured limit.");
        if (minMinutes < properties.minDurationMinutes() || minMinutes > (long) properties.maxDurationHours() * 60)
            throw ApiException.invalid("Minimum search duration is outside booking duration limits.");
    }
}

