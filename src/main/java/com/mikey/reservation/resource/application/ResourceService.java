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
    public ResourceService(ResourceRepository repository, Clock clock) {
        this.repository = repository; this.clock = clock;
    }

    @Transactional
    public ResourceResponse create(ResourceRequest request) {
        return ResourceResponse.from(repository.save(new Resource(request.name(), request.description(), request.location(),
                clock.instant().truncatedTo(ChronoUnit.MICROS))));
    }
    @Transactional(readOnly = true)
    public ResourceResponse get(UUID id) { return ResourceResponse.from(required(id)); }

    @Transactional(readOnly = true)
    public PageResponse<ResourceResponse> list(int page, int size) {
        PageResponse.validate(page, size);
        return PageResponse.from(repository.findAll(PageRequest.of(page, size, Sort.by("name", "id")))
                .map(ResourceResponse::from));
    }
    @Transactional
    public ResourceResponse update(UUID id, ResourceRequest request) {
        Resource resource = required(id);
        resource.update(request.name(), request.description(), request.location(), clock.instant().truncatedTo(ChronoUnit.MICROS));
        return ResourceResponse.from(resource);
    }
    private Resource required(UUID id) {
        return repository.findById(id).orElseThrow(() -> ApiException.notFound("RESOURCE"));
    }
}

