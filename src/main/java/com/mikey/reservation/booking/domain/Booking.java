package com.mikey.reservation.booking.domain;

import com.mikey.reservation.shared.api.ApiException;
import org.springframework.http.HttpStatus;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.AccessLevel;

@Entity @Table(name = "bookings")
@Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Booking {
    @Id private UUID id;
    @Column(nullable = false, updatable = false) private UUID resourceId;
    @Column(nullable = false, updatable = false) private Instant startsAt;
    @Column(nullable = false, updatable = false) private Instant endsAt;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private BookingStatus status;
    @Column(nullable = false, updatable = false) private Instant createdAt;
    private Instant cancelledAt;

    public Booking(UUID resourceId, TimeInterval interval, Instant now) {
        this.id = UUID.randomUUID();
        this.resourceId = resourceId;
        this.startsAt = interval.start();
        this.endsAt = interval.end();
        this.status = BookingStatus.CONFIRMED;
        this.createdAt = now;
    }
    public boolean cancel(Instant now) {
        if (status == BookingStatus.CANCELLED) return false;
        if (!now.isBefore(startsAt)) throw new ApiException(HttpStatus.CONFLICT, "BOOKING_CANNOT_BE_CANCELLED",
                "A booking cannot be cancelled once it has started.");
        status = BookingStatus.CANCELLED;
        cancelledAt = now;
        return true;
    }
}

