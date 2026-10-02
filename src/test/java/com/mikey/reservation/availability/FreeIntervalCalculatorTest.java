package com.mikey.reservation.availability;

import com.mikey.reservation.availability.domain.FreeIntervalCalculator;
import com.mikey.reservation.booking.domain.TimeInterval;
import java.time.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class FreeIntervalCalculatorTest {
    private static final Instant BASE = Instant.parse("2030-01-01T00:00:00Z");
    private TimeInterval span(int from, int to) { return new TimeInterval(BASE.plusSeconds(from * 60L), BASE.plusSeconds(to * 60L)); }

    @Test void emptyOccupancyReturnsEntireWindow() {
        assertThat(FreeIntervalCalculator.calculate(span(0, 180), List.of(), Duration.ofMinutes(60))).containsExactly(span(0, 180));
    }
    @Test void clipsMergesAndSortsOccupiedIntervals() {
        var occupied = List.of(span(150, 240), span(30, 90), span(-30, 45), span(90, 120), span(240, 300));
        assertThat(FreeIntervalCalculator.calculate(span(0, 180), occupied, Duration.ofMinutes(30))).containsExactly(span(120, 150));
    }
    @Test void nestedBookingDoesNotMoveCursorBackwards() {
        assertThat(FreeIntervalCalculator.calculate(span(0, 180), List.of(span(0, 120), span(30, 60)), Duration.ofMinutes(1)))
                .containsExactly(span(120, 180));
    }
    @Test void fullyOccupiedWindowHasNoGaps() {
        assertThat(FreeIntervalCalculator.calculate(span(0, 180), List.of(span(-1, 181)), Duration.ofMinutes(1))).isEmpty();
    }
    @Test void returnsHeadAndTailAndFiltersShortGaps() {
        assertThat(FreeIntervalCalculator.calculate(span(0, 180), List.of(span(60, 150)), Duration.ofMinutes(30)))
                .containsExactly(span(0, 60), span(150, 180));
        assertThat(FreeIntervalCalculator.calculate(span(0, 180), List.of(span(60, 150)), Duration.ofMinutes(31)))
                .containsExactly(span(0, 60));
    }
}

