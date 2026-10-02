package com.mikey.reservation.availability.api;

import com.mikey.reservation.availability.application.AvailabilityService;
import com.mikey.reservation.booking.domain.TimeInterval;
import org.springframework.web.bind.annotation.*;
import org.springframework.format.annotation.DateTimeFormat;
import java.time.OffsetDateTime;
import java.util.UUID;

@RestController @RequestMapping("/api/v1/resources/{resourceId}/availability")
public class AvailabilityController {
    private final AvailabilityService service;
    public AvailabilityController(AvailabilityService service) { this.service = service; }
    @GetMapping
    public AvailabilityResponse find(@PathVariable UUID resourceId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            @RequestParam(defaultValue = "1") int minDurationMinutes) {
        return service.find(resourceId, new TimeInterval(from.toInstant(), to.toInstant()), minDurationMinutes);
    }
}

