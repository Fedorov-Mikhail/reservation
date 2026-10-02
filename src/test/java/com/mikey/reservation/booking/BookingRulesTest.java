package com.mikey.reservation.booking;

import com.mikey.reservation.booking.domain.*;
import com.mikey.reservation.shared.api.ApiException;
import com.mikey.reservation.shared.config.BookingPolicyProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.time.*;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

class BookingRulesTest {
    private static final Instant NOW = Instant.parse("2030-01-01T00:00:00Z");
    private final BookingPolicy policy = new BookingPolicy(new BookingPolicyProperties(1, 24, 365, 31));
    private TimeInterval interval(long start, long end) {
        return new TimeInterval(NOW.plusSeconds(start), NOW.plusSeconds(end));
    }

    @ParameterizedTest
    @CsvSource({"0,60,true", "30,90,true", "10,20,true", "-60,120,true", "60,120,false", "-60,0,false"})
    void halfOpenOverlap(long start, long end, boolean expected) {
        assertThat(interval(0, 60).overlaps(interval(start, end))).isEqualTo(expected);
    }

    @Test void rejectsEmptyReverseAndSubMicrosecondIntervals() {
        assertThatThrownBy(() -> interval(0, 0)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> interval(60, 0)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> new TimeInterval(NOW.plusNanos(1), NOW.plusSeconds(60))).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> new TimeInterval(NOW, NOW.plusSeconds(60).plusNanos(1))).isInstanceOf(ApiException.class);
    }

    @Test void validatesDurationAndHorizonBoundaries() {
        assertThatCode(() -> policy.validateCreation(interval(0, 60), NOW)).doesNotThrowAnyException();
        assertThatCode(() -> policy.validateCreation(interval(0, 86400), NOW)).doesNotThrowAnyException();
        assertThatThrownBy(() -> policy.validateCreation(interval(0, 59), NOW)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> policy.validateCreation(interval(0, 86401), NOW)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> policy.validateCreation(interval(-1, 60), NOW)).isInstanceOf(ApiException.class);
        long horizon = Duration.ofDays(365).toSeconds();
        assertThatCode(() -> policy.validateCreation(interval(horizon - 60, horizon), NOW)).doesNotThrowAnyException();
        assertThatThrownBy(() -> policy.validateCreation(interval(horizon - 59, horizon + 1), NOW)).isInstanceOf(ApiException.class);
    }

    @Test void searchWindowAndMinimumDurationAreBounded() {
        assertThatCode(() -> policy.validateSearch(interval(0, Duration.ofDays(31).toSeconds()), 60)).doesNotThrowAnyException();
        assertThatThrownBy(() -> policy.validateSearch(interval(0, Duration.ofDays(31).toSeconds() + 1), 60)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> policy.validateSearch(interval(0, 3600), 0)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> policy.validateSearch(interval(0, 3600), 1441)).isInstanceOf(ApiException.class);
    }

    @Test void cancellationIsTerminalEvenAfterOriginalStart() {
        Booking booking = new Booking(UUID.randomUUID(), interval(60, 120), NOW);
        assertThat(booking.cancel(NOW)).isTrue();
        assertThat(booking.cancel(NOW.plusSeconds(90))).isFalse();
        assertThat(booking.getCancelledAt()).isEqualTo(NOW);
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CANCELLED);
    }

    @Test void cancellationAtStartIsRejected() {
        Booking booking = new Booking(UUID.randomUUID(), interval(60, 120), NOW);
        assertThatThrownBy(() -> booking.cancel(NOW.plusSeconds(60))).isInstanceOf(ApiException.class);
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(booking.getCancelledAt()).isNull();
    }
}

