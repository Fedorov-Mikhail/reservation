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
    private final com.mikey.reservation.schedule.ScheduleService schedules;
    private final com.mikey.reservation.identity.CurrentUser current;
    public AvailabilityService(ResourceRepository resources, BookingRepository bookings, BookingPolicy policy, com.mikey.reservation.identity.CurrentUser current, com.mikey.reservation.schedule.ScheduleService schedules) {
        this.schedules = schedules; this.current = current; this.resources = resources; this.bookings = bookings; this.policy = policy;
    }
    @Transactional(readOnly = true)
    public AvailabilityResponse find(UUID resourceId, TimeInterval window, int minMinutes) {
        current.current(); policy.validateSearch(window, minMinutes);
        if (!resources.existsById(resourceId)) throw ApiException.notFound("RESOURCE");
        var occupied = bookings.occupied(resourceId, window.start(), window.end()).stream()
                .map(b -> new TimeInterval(b.getStartsAt(), b.getEndsAt())).toList();
        var free = schedules.openIntervals(resourceId, window).stream().flatMap(open -> FreeIntervalCalculator.calculate(open, occupied, Duration.ofMinutes(minMinutes)).stream())
                .map(i -> new AvailabilityResponse.Interval(i.start(), i.end())).toList();
        return new AvailabilityResponse(resourceId, window.start(), window.end(), free);
    }
}



