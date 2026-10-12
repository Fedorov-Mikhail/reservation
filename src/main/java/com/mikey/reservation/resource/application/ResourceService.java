package com.mikey.reservation.resource.application;

import com.mikey.reservation.resource.api.*;
import com.mikey.reservation.resource.domain.Resource;
import com.mikey.reservation.resource.persistence.ResourceRepository;
import com.mikey.reservation.shared.api.*;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

@Service
public class ResourceService {
    private final ResourceRepository repository;
    private final Clock clock;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;
    private final com.mikey.reservation.identity.CurrentUser current;
    public ResourceService(ResourceRepository repository, Clock clock, com.mikey.reservation.identity.CurrentUser current, org.springframework.jdbc.core.JdbcTemplate jdbc) {
        this.jdbc = jdbc; this.current = current; this.repository = repository; this.clock = clock;
    }

    @Transactional
    public ResourceResponse create(ResourceRequest request) {
        current.requireAdmin();
        Resource resource = new Resource(request.name(), request.description(), request.location(), clock.instant().truncatedTo(ChronoUnit.MICROS));
        resource.assignOwner(current.requiredId());
        return ResourceResponse.from(repository.save(resource));
    }
    @Transactional(readOnly = true)
    public ResourceResponse get(UUID id) { current.current(); return ResourceResponse.from(required(id)); }

    @Transactional(readOnly = true)
    public PageResponse<ResourceResponse> list(int page, int size) {
        return list(page, size, "", false);
    }
    @Transactional(readOnly = true)
    public PageResponse<ResourceResponse> list(int page, int size, String q, boolean archived) {
        current.current(); if(archived) current.requireAdmin(); PageResponse.validate(page, size);
        if(q == null || q.length() > 120) throw ApiException.invalid("Search must be at most 120 characters.");
        return PageResponse.from(repository.catalog(q.strip().toLowerCase(java.util.Locale.ROOT), archived, PageRequest.of(page, size, Sort.by("name", "id")))
                .map(ResourceResponse::from));
    }
    @Transactional
    public ResourceResponse update(UUID id, ResourceRequest request) {
        current.requireManager(id); Resource resource = repository.findLockedById(id).orElseThrow(() -> ApiException.notFound("RESOURCE"));
        resource.requireBookable();
        resource.update(request.name(), request.description(), request.location(), clock.instant().truncatedTo(ChronoUnit.MICROS));
        return ResourceResponse.from(resource);
    }
    @Transactional
    public void delete(UUID id) {
        current.requireAdmin();
        Resource resource = repository.findLockedById(id).orElseThrow(() -> ApiException.notFound("RESOURCE"));
        boolean hasHistory = Boolean.TRUE.equals(jdbc.queryForObject(
                "select exists(select 1 from bookings where resource_id=?) or exists(select 1 from waitlist_entries where resource_id=?)",
                Boolean.class, id, id));
        if (hasHistory) throw new ApiException(org.springframework.http.HttpStatus.CONFLICT, "RESOURCE_IN_USE",
                "Resource has booking or waitlist history and cannot be deleted.");
        jdbc.update("delete from resource_schedules where resource_id=?", id);
        jdbc.update("delete from resource_managers where resource_id=?", id);
        repository.delete(resource);
        repository.flush();
    }
    @Transactional
    public ResourceResponse archive(UUID id) {
        current.requireAdmin();
        Resource resource = repository.findLockedById(id).orElseThrow(() -> ApiException.notFound("RESOURCE"));
        if(resource.isArchived()) return ResourceResponse.from(resource);
        java.sql.Timestamp now = java.sql.Timestamp.from(clock.instant());
        if(Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from bookings where resource_id=? and ends_at>? and (status='CONFIRMED' or (status='HELD' and expires_at>?)))", Boolean.class,id,now,now)))
            throw new ApiException(org.springframework.http.HttpStatus.CONFLICT,"RESOURCE_HAS_ACTIVE_BOOKINGS","Cancel or finish active bookings first.");
        if(Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from waitlist_entries where resource_id=? and status in ('WAITING','OFFERED') and ends_at>?)", Boolean.class,id,now)))
            throw new ApiException(org.springframework.http.HttpStatus.CONFLICT,"RESOURCE_HAS_ACTIVE_REQUESTS","Cancel or finish waitlist requests first.");
        resource.archive(clock.instant().truncatedTo(ChronoUnit.MICROS),current.requiredId());
        return ResourceResponse.from(resource);
    }
    @Transactional
    public ResourceResponse restore(UUID id) {
        current.requireAdmin();
        Resource resource = repository.findLockedById(id).orElseThrow(() -> ApiException.notFound("RESOURCE"));
        if(resource.isArchived()) resource.restore(clock.instant().truncatedTo(ChronoUnit.MICROS));
        return ResourceResponse.from(resource);
    }
    private Resource required(UUID id) {
        return repository.findById(id).orElseThrow(() -> ApiException.notFound("RESOURCE"));
    }
}



