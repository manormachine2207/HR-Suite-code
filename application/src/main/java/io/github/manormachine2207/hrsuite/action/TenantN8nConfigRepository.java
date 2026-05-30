package io.github.manormachine2207.hrsuite.action;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface TenantN8nConfigRepository extends JpaRepository<TenantN8nConfig, UUID> {
}
