package io.github.manormachine2207.hrsuite.platform;

import org.springframework.data.jpa.repository.JpaRepository;

/** Repository for the OIDC config singleton (ADR-019 Stufe 2). */
public interface OidcConfigRepository extends JpaRepository<OidcConfig, Short> {
}
