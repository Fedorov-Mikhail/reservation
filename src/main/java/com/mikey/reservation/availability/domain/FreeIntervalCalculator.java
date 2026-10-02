package com.mikey.reservation.availability.domain;

import com.mikey.reservation.booking.domain.TimeInterval;
import java.time.*;
import java.util.*;

public final class FreeIntervalCalculator {
    private FreeIntervalCalculator() {}
    public static List<TimeInterval> calculate(TimeInterval window, List<TimeInterval> occupied, Duration minimum) {
        List<TimeInterval> free = new ArrayList<>();
        Instant cursor = window.start();
        for (TimeInterval booking : occupied.stream().sorted(Comparator.comparing(TimeInterval::start)).toList()) {
            if (!booking.overlaps(window)) continue;
            Instant start = booking.start().isBefore(window.start()) ? window.start() : booking.start();
            Instant end = booking.end().isAfter(window.end()) ? window.end() : booking.end();
            if (cursor.isBefore(start) && Duration.between(cursor, start).compareTo(minimum) >= 0)
                free.add(new TimeInterval(cursor, start));
            if (end.isAfter(cursor)) cursor = end;
        }
        if (cursor.isBefore(window.end()) && Duration.between(cursor, window.end()).compareTo(minimum) >= 0)
            free.add(new TimeInterval(cursor, window.end()));
        return List.copyOf(free);
    }
}

