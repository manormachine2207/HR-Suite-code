package io.github.manormachine2207.hrsuite.antragstyp;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AntragsTypVersionRepository extends JpaRepository<AntragsTypVersion, UUID> {

    List<AntragsTypVersion> findByAntragstypIdOrderByMajorDesc(UUID antragstypId);

    Optional<AntragsTypVersion> findByAntragstypIdAndMajor(UUID antragstypId, int major);

    /**
     * Returns the single version for the given antragstyp that matches the given
     * status. This method is intended for the per-antragstyp-unique {@code PUBLISHED}
     * lookup (ADR-009: exactly one published major per antragstyp, enforced at the
     * service layer). Callers must not use it for statuses that may have multiple
     * rows (DRAFT, DEPRECATED, ARCHIVED) — Spring Data will throw
     * {@link org.springframework.dao.IncorrectResultSizeDataAccessException} if
     * more than one row matches.
     */
    Optional<AntragsTypVersion> findByAntragstypIdAndStatus(UUID antragstypId, VersionStatus status);

    /**
     * Resolves the parent antragstyp id for a version without loading the (mutable)
     * version entity into the persistence context. {@link AntragsTypService#publish}
     * uses it to derive the per-antragstyp advisory-lock key BEFORE reading any mutable
     * state, so the lock can be held across the read-modify-write. RLS-scoped like every
     * other query, so a cross-tenant version id resolves to {@link Optional#empty()}.
     */
    @Query("select v.antragstypId from AntragsTypVersion v where v.id = :id")
    Optional<UUID> findAntragstypIdById(@Param("id") UUID id);

    @Query("select coalesce(max(v.major), 0) from AntragsTypVersion v where v.antragstypId = :antragstypId")
    int maxMajor(@Param("antragstypId") UUID antragstypId);
}
