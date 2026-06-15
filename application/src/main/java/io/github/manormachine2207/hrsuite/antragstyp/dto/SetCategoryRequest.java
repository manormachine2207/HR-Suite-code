package io.github.manormachine2207.hrsuite.antragstyp.dto;

import io.github.manormachine2207.hrsuite.antragstyp.AntragsKategorie;

/** Body für PUT /api/v1/antragstyp/{id}/category (ADR-021). null = entkategorisieren. */
public record SetCategoryRequest(AntragsKategorie category) {
}
