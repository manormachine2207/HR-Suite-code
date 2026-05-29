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

    Optional<AntragsTypVersion> findByAntragstypIdAndStatus(UUID antragstypId, VersionStatus status);

    @Query("select coalesce(max(v.major), 0) from AntragsTypVersion v where v.antragstypId = :antragstypId")
    int maxMajor(@Param("antragstypId") UUID antragstypId);
}
