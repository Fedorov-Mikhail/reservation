package com.mikey.reservation.resource.api;

import com.mikey.reservation.resource.application.ResourceService;
import com.mikey.reservation.shared.api.PageResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import java.net.URI;
import java.util.UUID;

@RestController @RequestMapping("/api/v1/resources")
public class ResourceController {
    private final ResourceService service;
    public ResourceController(ResourceService service) { this.service = service; }
    @PostMapping
    public ResponseEntity<ResourceResponse> create(@Valid @RequestBody ResourceRequest body) {
        var result = service.create(body);
        return ResponseEntity.created(URI.create("/api/v1/resources/" + result.id())).body(result);
    }
    @GetMapping("/{id}") public ResourceResponse get(@PathVariable UUID id) { return service.get(id); }
    @GetMapping public PageResponse<ResourceResponse> list(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) { return service.list(page, size); }
    @PutMapping("/{id}") public ResourceResponse update(@PathVariable UUID id, @Valid @RequestBody ResourceRequest body) {
        return service.update(id, body);
    }
}

