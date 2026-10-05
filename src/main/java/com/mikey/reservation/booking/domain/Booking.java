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
    private Instant expiresAt;
    private Instant confirmedAt;
    @Column(nullable = false, updatable = false) private UUID createdBy = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private UUID cancelledBy;
    @Column(length = 500) private String cancellationReason;
    public void assignOwner(UUID owner) { this.createdBy = owner; }
    public void cancellationActor(UUID actor, String reason) { this.cancelledBy = actor; this.cancellationReason = reason; }

    public Booking(UUID resourceId, TimeInterval interval, Instant now) {
        this.id = UUID.randomUUID();
        this.resourceId = resourceId;
        this.startsAt = interval.start();
        this.endsAt = interval.end();
        this.status = BookingStatus.CONFIRMED; this.confirmedAt = now;
        this.createdAt = now;
    }
    public void hold(Instant deadline) { status = BookingStatus.HELD; expiresAt = deadline; confirmedAt = null; }
    public boolean expire(Instant now) {
        if(status == BookingStatus.HELD && !now.isBefore(expiresAt)) { status = BookingStatus.EXPIRED; return true; }
        return false;
    }
    public boolean confirm(Instant now) {
        if(status == BookingStatus.CONFIRMED) return false;
        if(status != BookingStatus.HELD || !now.isBefore(expiresAt)) throw new ApiException(HttpStatus.CONFLICT,"HOLD_EXPIRED","Hold is no longer available.");
        status = BookingStatus.CONFIRMED; confirmedAt = now; return true;
    }
    public boolean cancel(Instant now) {
        if (status == BookingStatus.CANCELLED) return false;
        if (status == BookingStatus.EXPIRED) throw new ApiException(HttpStatus.CONFLICT, "HOLD_EXPIRED", "Hold has expired.");
        if (!now.isBefore(startsAt)) throw new ApiException(HttpStatus.CONFLICT, "BOOKING_CANNOT_BE_CANCELLED",
                "A booking cannot be cancelled once it has started.");
        status = BookingStatus.CANCELLED;
        cancelledAt = now;
        return true;
    }
}



