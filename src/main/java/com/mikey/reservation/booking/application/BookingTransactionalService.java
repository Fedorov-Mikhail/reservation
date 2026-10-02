package com.mikey.reservation.booking.application;

import com.mikey.reservation.booking.api.BookingResponse;
import com.mikey.reservation.booking.domain.*;
import com.mikey.reservation.booking.persistence.BookingRepository;
import com.mikey.reservation.resource.persistence.ResourceRepository;
import com.mikey.reservation.shared.api.*;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.data.domain.*;

@Service
public class BookingTransactionalService {
    private final BookingRepository bookings;
    private final ResourceRepository resources;
    private final BookingPolicy policy;
    private final Clock clock;
    public BookingTransactionalService(BookingRepository bookings, ResourceRepository resources, BookingPolicy policy, Clock clock) {
        this.bookings = bookings; this.resources = resources; this.policy = policy; this.clock = clock;
    }
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public BookingResponse create(UUID resourceId, TimeInterval interval) {
        resources.findLockedById(resourceId).orElseThrow(() -> ApiException.notFound("RESOURCE"));
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        policy.validateCreation(interval, now);
        return BookingResponse.from(bookings.saveAndFlush(new Booking(resourceId, interval, now)));
    }
    public record Cancellation(BookingResponse booking, boolean changed) {}
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Cancellation cancel(UUID id) {
        Booking booking = bookings.findLockedById(id).orElseThrow(() -> ApiException.notFound("BOOKING"));
        boolean changed = booking.cancel(clock.instant().truncatedTo(ChronoUnit.MICROS));
        bookings.flush();
        return new Cancellation(BookingResponse.from(booking), changed);
    }
    @Transactional(readOnly = true)
    public BookingResponse get(UUID id) {
        return BookingResponse.from(bookings.findById(id).orElseThrow(() -> ApiException.notFound("BOOKING")));
    }
    @Transactional(readOnly = true)
    public PageResponse<BookingResponse> list(UUID resourceId, BookingStatus status, Instant from, Instant to, int page, int size) {
        PageResponse.validate(page, size);
        if ((from == null) != (to == null)) throw ApiException.invalid("from and to must be provided together.");
        if (from != null) new TimeInterval(from, to);
        requireResource(resourceId);
        Pageable pageable = PageRequest.of(page, size, Sort.by("startsAt", "id"));
        Page<Booking> result = from == null ? bookings.list(resourceId, status, pageable)
                : bookings.listOverlapping(resourceId, status, from, to, pageable);
        return PageResponse.from(result.map(BookingResponse::from));
    }
    private void requireResource(UUID id) {
        if (!resources.existsById(id)) throw ApiException.notFound("RESOURCE");
    }
}


