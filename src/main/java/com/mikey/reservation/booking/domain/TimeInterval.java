package com.mikey.reservation.booking.domain;

import com.mikey.reservation.shared.api.ApiException;
import java.time.Duration;
import java.time.Instant;

public record TimeInterval(Instant start, Instant end) {
    private static final Instant MIN = Instant.parse("0001-01-01T00:00:00Z");
    private static final Instant MAX = Instant.parse("9999-12-31T23:59:59.999999Z");

    public TimeInterval {
        if (start == null || end == null || !start.isBefore(end))
            throw ApiException.invalid("startsAt must be before endsAt.");
        if (start.isBefore(MIN) || end.isAfter(MAX))
            throw ApiException.invalid("Timestamps must be within UTC years 0001 through 9999.");
        if (start.getNano() % 1000 != 0 || end.getNano() % 1000 != 0)
            throw ApiException.invalid("Timestamp precision must not exceed microseconds.");
    }
    public boolean overlaps(TimeInterval other) {
        return start.isBefore(other.end) && other.start.isBefore(end);
    }
    public Duration duration() { return Duration.between(start, end); }
}

