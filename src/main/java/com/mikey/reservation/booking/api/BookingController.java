package com.mikey.reservation.booking.api;

import com.mikey.reservation.booking.application.*;
import com.mikey.reservation.booking.domain.BookingStatus;
import com.mikey.reservation.shared.api.PageResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.format.annotation.DateTimeFormat;
import java.net.URI;
import java.time.OffsetDateTime;
import java.util.UUID;

@RestController @RequestMapping("/api/v1")
public class BookingController {
    private final BookingApplicationService service;
    private final BookingTransactionalService queries;
    public BookingController(BookingApplicationService service, BookingTransactionalService queries) {
        this.service = service; this.queries = queries;
    }
    @PostMapping("/resources/{resourceId}/bookings")
    public ResponseEntity<BookingResponse> create(@PathVariable UUID resourceId, @Valid @RequestBody CreateBookingRequest body, @RequestHeader(value="Idempotency-Key", required=false) String key) {
        var result = service.create(resourceId, body.interval(), false, key);
        return ResponseEntity.created(URI.create("/api/v1/bookings/" + result.id())).body(result);
    }
    @PostMapping("/resources/{resourceId}/holds") public ResponseEntity<BookingResponse> hold(@PathVariable UUID resourceId, @Valid @RequestBody CreateBookingRequest body, @RequestHeader(value="Idempotency-Key", required=false) String key) {
        var result = service.create(resourceId, body.interval(), true, key);
        return ResponseEntity.created(URI.create("/api/v1/bookings/" + result.id())).body(result);
    }
    @PostMapping("/bookings/{id}/confirm") public BookingResponse confirm(@PathVariable UUID id) {return queries.confirm(id);}
    @GetMapping("/bookings/{id}") public BookingResponse get(@PathVariable UUID id) { return queries.get(id); }
    @PostMapping("/bookings/{id}/cancel") public BookingResponse cancel(@PathVariable UUID id, @RequestBody(required = false) CancellationRequest body) { return service.cancel(id, body == null ? null : body.reason()); }
    public record CancellationRequest(String reason) {}
    @GetMapping("/me/bookings") public PageResponse<BookingResponse> mine(@RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="20") int size) { return queries.mine(page, size); }

    @GetMapping("/resources/{resourceId}/bookings")
    public PageResponse<BookingResponse> list(@PathVariable UUID resourceId,
            @RequestParam(required = false) BookingStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return queries.list(resourceId, status, from == null ? null : from.toInstant(), to == null ? null : to.toInstant(), page, size);
    }
}



