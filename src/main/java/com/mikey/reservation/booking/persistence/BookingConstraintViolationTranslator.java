package com.mikey.reservation.booking.persistence;

import com.mikey.reservation.shared.api.ApiException;
import org.postgresql.util.PSQLException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class BookingConstraintViolationTranslator {
    public RuntimeException translate(RuntimeException failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof PSQLException sql && "23P01".equals(sql.getSQLState())
                    && sql.getServerErrorMessage() != null
                    && "bookings_no_confirmed_overlap".equals(sql.getServerErrorMessage().getConstraint())) {
                return new ApiException(HttpStatus.CONFLICT, "BOOKING_OVERLAP",
                        "The requested interval overlaps an existing booking.");
            }
        }
        return failure;
    }
}

