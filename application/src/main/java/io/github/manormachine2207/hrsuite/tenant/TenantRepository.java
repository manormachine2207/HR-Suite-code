package io.github.manormachine2207.hrsuite.tenant;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface TenantRepository extends JpaRepository<Tenant, UUID> {

    boolean existsByCode(String code);

    boolean existsBySubdomain(String subdomain);

    Optional<Tenant> findByCode(String code);
}
