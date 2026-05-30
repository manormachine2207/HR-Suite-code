package io.github.manormachine2207.hrsuite.antrag;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * All queries are RLS-scoped to the current tenant (ADR-008); the methods below add
 * the in-tenant filters (own requests by subject, tenant-wide listing).
 */
public interface AntragRepository extends JpaRepository<Antrag, UUID> {

    List<Antrag> findByAntragstellerSubjectOrderByCreatedAtDesc(String antragstellerSubject);

    List<Antrag> findAllByOrderByCreatedAtDesc();
}
