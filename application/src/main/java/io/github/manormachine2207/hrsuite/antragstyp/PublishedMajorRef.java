package io.github.manormachine2207.hrsuite.antragstyp;

import java.util.UUID;

/**
 * Minimal, cross-module-safe reference to an antragstyp's currently published major:
 * the major version id to pin, the minor it currently sits at, and the Flowable
 * process-definition key to start an instance from (null if not deployed). Returned by
 * {@link AntragsTypService#findPublishedMajor(UUID)} so the antrag module can pin a
 * submission (ADR-009 §4) and start its process (§5) without depending on the
 * AntragsTypVersion entity.
 */
public record PublishedMajorRef(UUID versionId, int minor, String processDefinitionKey) {
}
