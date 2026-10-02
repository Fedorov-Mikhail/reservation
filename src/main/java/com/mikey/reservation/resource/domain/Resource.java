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

