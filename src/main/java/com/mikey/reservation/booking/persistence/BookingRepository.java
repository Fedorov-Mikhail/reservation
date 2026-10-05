package com.mikey.reservation.booking.persistence;

import com.mikey.reservation.booking.domain.*;
import java.time.Instant;
import java.util.*;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.domain.*;

public interface BookingRepository extends JpaRepository<Booking, UUID> {
    @Query("select b.resourceId from Booking b where b.id = :id")
    Optional<UUID> resourceId(UUID id);
    List<Booking> findByResourceIdAndStatus(UUID resourceId, BookingStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from Booking b where b.id = :id")
    Optional<Booking> findLockedById(UUID id);

    @Query("select b from Booking b where b.resourceId = :resourceId and (:owner is null or b.createdBy = :owner) and (:status is null or b.status = :status)")
    Page<Booking> list(UUID resourceId, UUID owner, BookingStatus status, Pageable pageable);

    @Query("select b from Booking b where b.resourceId = :resourceId and (:owner is null or b.createdBy = :owner) and (:status is null or b.status = :status) and b.startsAt < :to and b.endsAt > :from")
    Page<Booking> listOverlapping(UUID resourceId, UUID owner, BookingStatus status, Instant from, Instant to, Pageable pageable);

    Page<Booking> findByCreatedBy(UUID owner, Pageable pageable);

    @Query(value = "select * from bookings where resource_id = :resourceId and status IN ('CONFIRMED','HELD') and tstzrange(starts_at, ends_at, '[)') && tstzrange(:from, :to, '[)') order by starts_at, id", nativeQuery = true)
    List<Booking> occupied(UUID resourceId, Instant from, Instant to);
}



