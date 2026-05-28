package io.github.manormachine2207.hrsuite.tenant;

import com.github.f4b6a3.uuid.UuidCreator;
import io.github.manormachine2207.hrsuite.tenant.TenantExceptions.TenantConflictException;
import io.github.manormachine2207.hrsuite.tenant.TenantExceptions.TenantNotFoundException;
import io.github.manormachine2207.hrsuite.tenant.dto.CreateTenantRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class TenantService {

    private final TenantRepository repository;

    public TenantService(TenantRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public Tenant create(CreateTenantRequest request) {
        if (repository.existsByCode(request.code())) {
            throw new TenantConflictException("code", request.code());
        }
        if (repository.existsBySubdomain(request.subdomain())) {
            throw new TenantConflictException("subdomain", request.subdomain());
        }
        Tenant tenant = new Tenant(
                UuidCreator.getTimeOrderedEpoch(),
                request.code(),
                request.displayName(),
                request.subdomain(),
                request.status() != null ? request.status() : TenantStatus.ACTIVE,
                request.defaultLocale() != null ? request.defaultLocale() : "de"
        );
        return repository.save(tenant);
    }

    @Transactional(readOnly = true)
    public List<Tenant> findAll() {
        return repository.findAll();
    }

    @Transactional(readOnly = true)
    public Tenant findById(UUID id) {
        return repository.findById(id).orElseThrow(() -> new TenantNotFoundException(id));
    }
}
