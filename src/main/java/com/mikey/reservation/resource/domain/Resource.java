package com.mikey.reservation.resource.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.AccessLevel;

@Entity @Table(name = "resources")
@Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Resource {
    @Id private UUID id;
    @Column(nullable = false, length = 120) private String name;
    @Column(length = 2000) private String description;
    @Column(length = 255) private String location;
    @Column(nullable = false, updatable = false) private Instant createdAt;
    @Column(nullable = false) private Instant updatedAt;
    @Column(nullable = false, updatable = false) private UUID createdBy = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private Instant archivedAt;
    private UUID archivedBy;
    public boolean isArchived() { return archivedAt != null; }
    public void archive(Instant now, UUID actor) { archivedAt = now; archivedBy = actor; updatedAt = now; }
    public void restore(Instant now) { archivedAt = null; archivedBy = null; updatedAt = now; }
    public void requireBookable() {
        if (isArchived()) throw new com.mikey.reservation.shared.api.ApiException(org.springframework.http.HttpStatus.CONFLICT, "RESOURCE_ARCHIVED", "Resource is archived.");
    }
    public void assignOwner(UUID owner) { this.createdBy = owner; }

    public Resource(String name, String description, String location, Instant now) {
        id = UUID.randomUUID();
        createdAt = now;
        update(name, description, location, now);
    }
    public void update(String name, String description, String location, Instant now) {
        this.name = name.strip();
        this.description = description;
        this.location = location;
        this.updatedAt = now;
    }
}


