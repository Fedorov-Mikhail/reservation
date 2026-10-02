package com.mikey.reservation.booking.api;
import com.mikey.reservation.booking.domain.*;
import java.time.Instant;
import java.util.UUID;
public record BookingResponse(UUID id, UUID resourceId, Instant startsAt, Instant endsAt, BookingStatus status,
                              Instant createdAt, Instant cancelledAt) {
    public static BookingResponse from(Booking b) {
        return new BookingResponse(b.getId(), b.getResourceId(), b.getStartsAt(), b.getEndsAt(), b.getStatus(),
                b.getCreatedAt(), b.getCancelledAt());
    }
}

