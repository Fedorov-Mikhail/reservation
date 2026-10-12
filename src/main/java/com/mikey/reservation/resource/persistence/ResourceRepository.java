package com.mikey.reservation.resource.persistence;

import com.mikey.reservation.resource.domain.Resource;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;

public interface ResourceRepository extends JpaRepository<Resource, UUID> {
    @Query("select r from Resource r where ((:archived = true and r.archivedAt is not null) or (:archived = false and r.archivedAt is null)) and (locate(:q, lower(r.name)) > 0 or locate(:q, lower(coalesce(r.location, ''))) > 0)")
    org.springframework.data.domain.Page<Resource> catalog(String q, boolean archived, org.springframework.data.domain.Pageable pageable);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Resource r where r.id = :id")
    Optional<Resource> findLockedById(UUID id);
}
