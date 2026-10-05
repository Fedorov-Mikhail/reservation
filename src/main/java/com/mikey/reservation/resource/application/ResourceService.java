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
    private final com.mikey.reservation.identity.CurrentUser current;
    public ResourceService(ResourceRepository repository, Clock clock, com.mikey.reservation.identity.CurrentUser current) {
        this.current = current; this.repository = repository; this.clock = clock;
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
        current.current(); PageResponse.validate(page, size);
        return PageResponse.from(repository.findAll(PageRequest.of(page, size, Sort.by("name", "id")))
                .map(ResourceResponse::from));
    }
    @Transactional
    public ResourceResponse update(UUID id, ResourceRequest request) {
        current.requireManager(id); Resource resource = required(id);
        resource.update(request.name(), request.description(), request.location(), clock.instant().truncatedTo(ChronoUnit.MICROS));
        return ResourceResponse.from(resource);
    }
    private Resource required(UUID id) {
        return repository.findById(id).orElseThrow(() -> ApiException.notFound("RESOURCE"));
    }
}



