package com.mikey.reservation.booking.application;

import com.mikey.reservation.booking.api.BookingResponse;
import com.mikey.reservation.booking.domain.TimeInterval;
import com.mikey.reservation.booking.persistence.BookingConstraintViolationTranslator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import java.util.UUID;

@Service
public class BookingApplicationService {
    private static final Logger log = LoggerFactory.getLogger(BookingApplicationService.class);
    private final BookingTransactionalService transactions;
    private final BookingConstraintViolationTranslator translator;
    public BookingApplicationService(BookingTransactionalService transactions, BookingConstraintViolationTranslator translator) {
        this.transactions = transactions; this.translator = translator;
    }
    public BookingResponse create(UUID resourceId, TimeInterval interval) {
        // Translation happens outside the transactional proxy, after rollback (including commit failures).
        try {
            BookingResponse response = transactions.create(resourceId, interval);
            log.info("booking_created bookingId={} resourceId={}", response.id(), resourceId);
            return response;
        } catch (RuntimeException failure) { throw translator.translate(failure); }
    }
    public BookingResponse cancel(UUID id) {
        var result = transactions.cancel(id);
        if (result.changed()) log.info("booking_cancelled bookingId={} resourceId={}", id, result.booking().resourceId());
        return result.booking();
    }
}

