package com.mikey.reservation.availability.api;
import java.time.Instant;
import java.util.*;
public record AvailabilityResponse(UUID resourceId, Instant from, Instant to, List<Interval> intervals) {
    public record Interval(Instant startsAt, Instant endsAt) {}
}

