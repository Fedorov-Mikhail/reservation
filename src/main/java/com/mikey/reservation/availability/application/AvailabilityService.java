package com.mikey.reservation.availability.application;

import com.mikey.reservation.availability.api.AvailabilityResponse;
import com.mikey.reservation.availability.domain.FreeIntervalCalculator;
import com.mikey.reservation.booking.domain.*;
import com.mikey.reservation.booking.persistence.BookingRepository;
import com.mikey.reservation.resource.persistence.ResourceRepository;
import com.mikey.reservation.shared.api.ApiException;
import java.time.Duration;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AvailabilityService {
    private final ResourceRepository resources;
    private final BookingRepository bookings;
    private final BookingPolicy policy;
    public AvailabilityService(ResourceRepository resources, BookingRepository bookings, BookingPolicy policy) {
        this.resources = resources; this.bookings = bookings; this.policy = policy;
    }
    @Transactional(readOnly = true)
    public AvailabilityResponse find(UUID resourceId, TimeInterval window, int minMinutes) {
        policy.validateSearch(window, minMinutes);
        if (!resources.existsById(resourceId)) throw ApiException.notFound("RESOURCE");
        var occupied = bookings.occupied(resourceId, window.start(), window.end()).stream()
                .map(b -> new TimeInterval(b.getStartsAt(), b.getEndsAt())).toList();
        var free = FreeIntervalCalculator.calculate(window, occupied, Duration.ofMinutes(minMinutes)).stream()
                .map(i -> new AvailabilityResponse.Interval(i.start(), i.end())).toList();
        return new AvailabilityResponse(resourceId, window.start(), window.end(), free);
    }
}

