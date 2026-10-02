package com.mikey.reservation.booking.api;

import com.mikey.reservation.booking.domain.TimeInterval;
import com.mikey.reservation.shared.api.ApiException;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import org.springframework.http.HttpStatus;

/** Parse ISO strings explicitly: the default Java-time JSON decoder also accepts epoch numbers. */
public record CreateBookingRequest(@NotNull @Size(max = 40) String startsAt,
                                   @NotNull @Size(max = 40) String endsAt) {
    public TimeInterval interval() {
        try {
            return new TimeInterval(OffsetDateTime.parse(startsAt).toInstant(), OffsetDateTime.parse(endsAt).toInstant());
        } catch (DateTimeParseException error) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST",
                    "startsAt and endsAt must be ISO 8601 strings with an explicit UTC offset.");
        }
    }
}

