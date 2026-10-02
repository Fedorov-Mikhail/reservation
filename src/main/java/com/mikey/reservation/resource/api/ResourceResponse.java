package com.mikey.reservation.resource.api;
import com.mikey.reservation.resource.domain.Resource;
import java.time.Instant;
import java.util.UUID;
public record ResourceResponse(UUID id, String name, String description, String location, Instant createdAt, Instant updatedAt) {
    public static ResourceResponse from(Resource r) {
        return new ResourceResponse(r.getId(), r.getName(), r.getDescription(), r.getLocation(), r.getCreatedAt(), r.getUpdatedAt());
    }
}

