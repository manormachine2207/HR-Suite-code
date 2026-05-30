package io.github.manormachine2207.hrsuite.antragstyp;

import java.util.UUID;

/**
 * Minimal, cross-module-safe reference to an antragstyp's currently published major:
 * the major version id to pin and the minor it currently sits at. Returned by
 * {@link AntragsTypService#findPublishedMajor(UUID)} so the antrag module can pin a
 * submission (ADR-009 §4) without depending on the AntragsTypVersion entity.
 */
public record PublishedMajorRef(UUID versionId, int minor) {
}
