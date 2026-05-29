package io.github.manormachine2207.hrsuite.antragstyp.form;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Provisorische Validierungs-Schranken. Alle Felder nullable = keine Schranke.
 * Referenziert von ADR-009 (maxLength rauf/runter, Range weiten/verengen).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Validation(
        Integer maxLength,
        Integer min,
        Integer max) {
}
