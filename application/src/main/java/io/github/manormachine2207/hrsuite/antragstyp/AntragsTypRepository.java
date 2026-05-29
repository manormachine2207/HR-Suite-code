package io.github.manormachine2207.hrsuite.antragstyp;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AntragsTypRepository extends JpaRepository<AntragsTyp, UUID> {

    boolean existsByKey(String key);

    Optional<AntragsTyp> findByKey(String key);

    List<AntragsTyp> findAllByOrderByCreatedAtDesc();
}
