package io.github.manormachine2207.hrsuite.tenant;

import io.github.manormachine2207.hrsuite.tenant.dto.ChangeTenantStatusRequest;
import io.github.manormachine2207.hrsuite.tenant.dto.CreateTenantRequest;
import io.github.manormachine2207.hrsuite.tenant.dto.TenantResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tenant")
public class TenantController {

    private final TenantService service;

    public TenantController(TenantService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<TenantResponse> create(@Valid @RequestBody CreateTenantRequest request,
                                                 UriComponentsBuilder uri) {
        Tenant created = service.create(request);
        URI location = uri.path("/api/v1/tenant/{id}").buildAndExpand(created.getId()).toUri();
        return ResponseEntity.created(location).body(TenantResponse.from(created));
    }

    @GetMapping
    public List<TenantResponse> list() {
        return service.findAll().stream().map(TenantResponse::from).toList();
    }

    @GetMapping("/{id}")
    public TenantResponse get(@PathVariable("id") UUID id) {
        return TenantResponse.from(service.findById(id));
    }

    /** Mandanten-Lebenszyklus verwalten (ADR-019 Stufe 1); platform-admin via SecurityConfig. */
    @PatchMapping("/{id}/status")
    public TenantResponse changeStatus(@PathVariable("id") UUID id,
                                       @Valid @RequestBody ChangeTenantStatusRequest request) {
        return TenantResponse.from(service.changeStatus(id, request.status()));
    }
}
