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
    private final com.mikey.reservation.waitlist.WaitlistService waitlist;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;
    private final com.mikey.reservation.schedule.ScheduleService schedules;
    private final com.mikey.reservation.identity.CurrentUser current;
    public BookingTransactionalService(BookingRepository bookings, ResourceRepository resources, BookingPolicy policy, Clock clock, com.mikey.reservation.identity.CurrentUser current, com.mikey.reservation.schedule.ScheduleService schedules, org.springframework.jdbc.core.JdbcTemplate jdbc, com.mikey.reservation.waitlist.WaitlistService waitlist) {
        this.waitlist = waitlist; this.jdbc = jdbc; this.schedules = schedules; this.current = current; this.bookings = bookings; this.resources = resources; this.policy = policy; this.clock = clock;
    }
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public BookingResponse create(UUID resourceId, TimeInterval interval) { return create(resourceId, interval, false, null); }
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public BookingResponse create(UUID resourceId, TimeInterval interval, boolean hold, String key) {
        UUID owner = current.requiredId();
        String fingerprint = resourceId + ":" + interval.start() + ":" + interval.end() + ":" + hold;
        if(key != null) {
            if(!key.matches("[A-Za-z0-9_-]{8,100}")) throw ApiException.invalid("Invalid Idempotency-Key.");
            jdbc.queryForList("select pg_advisory_xact_lock(hashtextextended(?,0))", owner + ":" + key);
            var previous = jdbc.queryForList("select fingerprint,booking_id from booking_requests where user_id=? and request_key=?", owner, key);
            if(!previous.isEmpty()) {
                if(!fingerprint.equals(previous.getFirst().get("fingerprint"))) throw new ApiException(org.springframework.http.HttpStatus.CONFLICT,"IDEMPOTENCY_CONFLICT","Key was used for another request.");
                return get((UUID)previous.getFirst().get("booking_id"));
            }
        }
        resources.findLockedById(resourceId).orElseThrow(() -> ApiException.notFound("RESOURCE"));
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS); expireLocked(resourceId, now); waitlist.promoteLocked(resourceId, now);
        policy.validateCreation(interval, now); schedules.validateBooking(resourceId, interval);
        var booking = new Booking(resourceId, interval, now);
        booking.assignOwner(owner);
        if(hold) booking.hold(now.plusSeconds(300).isBefore(interval.start()) ? now.plusSeconds(300) : interval.start());
        bookings.saveAndFlush(booking);
        if(key != null) jdbc.update("insert into booking_requests values (?,?,?,?)", owner, key, fingerprint, booking.getId());
        return BookingResponse.from(booking);
    }
    public record Cancellation(BookingResponse booking, boolean changed) {}
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Cancellation cancel(UUID id, String reason) {
        UUID resourceId = bookings.resourceId(id).orElseThrow(() -> ApiException.notFound("BOOKING"));
        resources.findLockedById(resourceId).orElseThrow(() -> ApiException.notFound("RESOURCE"));
        Booking booking = bookings.findLockedById(id).orElseThrow(() -> ApiException.notFound("BOOKING"));
        current.requireOwner(booking.getCreatedBy());
        if (!current.requiredId().equals(booking.getCreatedBy()) && (reason == null || reason.isBlank())) throw ApiException.invalid("Administrative cancellation requires a reason.");
        if (reason != null && reason.length() > 500) throw ApiException.invalid("Cancellation reason is too long.");
        boolean changed = booking.cancel(clock.instant().truncatedTo(ChronoUnit.MICROS));
        if (changed) booking.cancellationActor(current.requiredId(), reason);
        bookings.flush();
        waitlist.promoteLocked(resourceId, clock.instant());
        return new Cancellation(BookingResponse.from(booking), changed);
    }
    @Transactional(readOnly = true)
    public BookingResponse get(UUID id) {
        var booking = bookings.findById(id).orElseThrow(() -> ApiException.notFound("BOOKING"));
        current.requireOwner(booking.getCreatedBy()); return BookingResponse.from(booking);
    }
    @Transactional(readOnly = true)
    public PageResponse<BookingResponse> list(UUID resourceId, BookingStatus status, Instant from, Instant to, int page, int size) {
        PageResponse.validate(page, size);
        if ((from == null) != (to == null)) throw ApiException.invalid("from and to must be provided together.");
        if (from != null) new TimeInterval(from, to);
        requireResource(resourceId);
        Pageable pageable = PageRequest.of(page, size, Sort.by("startsAt", "id"));
        UUID owner = current.isAdmin() ? null : current.requiredId();
        Page<Booking> result = from == null ? bookings.list(resourceId, owner, status, pageable)
                : bookings.listOverlapping(resourceId, owner, status, from, to, pageable);
        return PageResponse.from(result.map(BookingResponse::from));
    }
    @Transactional(readOnly = true)
    public PageResponse<BookingResponse> mine(int page, int size) {
        PageResponse.validate(page, size);
        return PageResponse.from(bookings.findByCreatedBy(current.requiredId(), PageRequest.of(page, size, Sort.by("startsAt", "id"))).map(BookingResponse::from));
    }
    @Transactional
    public BookingResponse confirm(UUID id) {
        UUID resourceId = bookings.resourceId(id).orElseThrow(() -> ApiException.notFound("BOOKING"));
        resources.findLockedById(resourceId).orElseThrow(() -> ApiException.notFound("RESOURCE"));
        Booking booking = bookings.findLockedById(id).orElseThrow(() -> ApiException.notFound("BOOKING"));
        current.requireOwner(booking.getCreatedBy());
        booking.confirm(clock.instant()); bookings.flush(); waitlist.promoteLocked(resourceId, clock.instant()); return BookingResponse.from(booking);
    }
    @Transactional
    public void expireResource(UUID id) {
        if(resources.findLockedById(id).isPresent()) { expireLocked(id, clock.instant()); waitlist.promoteLocked(id, clock.instant()); }
    }
    private void expireLocked(UUID resourceId, Instant now) {
        for(var booking : bookings.findByResourceIdAndStatus(resourceId, BookingStatus.HELD)) booking.expire(now);
        bookings.flush();
    }
    private void requireResource(UUID id) {
        if (!resources.existsById(id)) throw ApiException.notFound("RESOURCE");
    }
}






