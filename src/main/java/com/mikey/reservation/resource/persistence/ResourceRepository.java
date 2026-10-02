package com.mikey.reservation.resource.persistence;

import com.mikey.reservation.resource.domain.Resource;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;

public interface ResourceRepository extends JpaRepository<Resource, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Resource r where r.id = :id")
    Optional<Resource> findLockedById(UUID id);
}
